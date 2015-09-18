/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.testkit.runner.internal;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.gradle.api.Action;
import org.gradle.internal.SystemProperties;
import org.gradle.testkit.runner.*;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DefaultGradleRunner extends GradleRunner {

    public static final String DIAGNOSTICS_MESSAGE_SEPARATOR = "-----";
    private final File gradleHome;
    private final GradleExecutor gradleExecutor;

    private TestKitDirProvider testKitDirProvider;

    private File projectDirectory;
    private List<String> arguments = new ArrayList<String>();
    private List<String> jvmArguments = new ArrayList<String>();
    private List<URI> classpath = new ArrayList<URI>();
    private boolean debug;

    public DefaultGradleRunner(File gradleHome) {
        this(gradleHome, new TestKitGradleExecutor(), new TempTestKitDirProvider());
    }

    DefaultGradleRunner(File gradleHome, GradleExecutor gradleExecutor, TestKitDirProvider testKitDirProvider) {
        this.gradleHome = gradleHome;
        this.gradleExecutor = gradleExecutor;
        this.testKitDirProvider = testKitDirProvider;
        debug = isDebugEnabled();
    }

    private boolean isDebugEnabled() {
        return Boolean.parseBoolean(System.getProperty(DEBUG_SYS_PROP, "false"));
    }

    public TestKitDirProvider getTestKitDirProvider() {
        return testKitDirProvider;
    }

    @Override
    public DefaultGradleRunner withTestKitDir(final File testKitDir) {
        if (testKitDir == null) {
            throw new IllegalArgumentException("testKitDir argument cannot be null");
        }
        this.testKitDirProvider = new ConstantTestKitDirProvider(testKitDir);
        return this;
    }

    public DefaultGradleRunner withJvmArguments(List<String> jvmArguments) {
        this.jvmArguments = new ArrayList<String>(jvmArguments);
        return this;
    }

    public DefaultGradleRunner withJvmArguments(String... jvmArguments) {
        return withJvmArguments(Arrays.asList(jvmArguments));
    }

    @Override
    public File getProjectDir() {
        return projectDirectory;
    }

    @Override
    public DefaultGradleRunner withProjectDir(File projectDir) {
        this.projectDirectory = projectDir;
        return this;
    }

    @Override
    public List<String> getArguments() {
        return Collections.unmodifiableList(arguments);
    }

    @Override
    public DefaultGradleRunner withArguments(List<String> arguments) {
        this.arguments = new ArrayList<String>(arguments);
        return this;
    }

    @Override
    public DefaultGradleRunner withArguments(String... arguments) {
        return withArguments(Arrays.asList(arguments));
    }

    @Override
    public List<URI> getClasspath() {
        return Collections.unmodifiableList(classpath);
    }

    @Override
    public GradleRunner withClasspath(List<URI> classpath) {
        this.classpath = new ArrayList<URI>(classpath);
        return this;
    }

    @Override
    public boolean isDebug() {
        return debug;
    }

    @Override
    public GradleRunner withDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    @Override
    public BuildResult build() {
        return run(new Action<GradleExecutionResult>() {
            public void execute(GradleExecutionResult gradleExecutionResult) {
                if (!gradleExecutionResult.isSuccessful()) {
                    throw new UnexpectedBuildFailure(createDiagnosticsMessage("Unexpected build execution failure", gradleExecutionResult));
                }
            }
        });
    }

    @Override
    public BuildResult buildAndFail() {
        return run(new Action<GradleExecutionResult>() {
            public void execute(GradleExecutionResult gradleExecutionResult) {
                if (gradleExecutionResult.isSuccessful()) {
                    throw new UnexpectedBuildSuccess(createDiagnosticsMessage("Unexpected build execution success", gradleExecutionResult));
                }
            }
        });
    }

    private String createDiagnosticsMessage(String trailingMessage, GradleExecutionResult gradleExecutionResult) {
        String lineBreak = SystemProperties.getInstance().getLineSeparator();
        StringBuilder message = new StringBuilder();
        message.append(trailingMessage);
        message.append(" in ");
        message.append(getProjectDir().getAbsolutePath());
        message.append(" with arguments ");
        message.append(getArguments());
        message.append(lineBreak).append(lineBreak);
        message.append("Output:");
        message.append(lineBreak);
        message.append(gradleExecutionResult.getStandardOutput());
        message.append(lineBreak);
        message.append(DIAGNOSTICS_MESSAGE_SEPARATOR);
        message.append(lineBreak);
        message.append("Error:");
        message.append(lineBreak);
        message.append(gradleExecutionResult.getStandardError());
        message.append(lineBreak);
        message.append(DIAGNOSTICS_MESSAGE_SEPARATOR);

        if (gradleExecutionResult.getThrowable() != null) {
            message.append(lineBreak);
            message.append("Reason:");
            message.append(lineBreak);
            message.append(determineExceptionMessage(gradleExecutionResult.getThrowable()));
            message.append(lineBreak);
            message.append(DIAGNOSTICS_MESSAGE_SEPARATOR);
        }

        return message.toString();
    }

    private String determineExceptionMessage(Throwable throwable) {
        return throwable.getCause() == null ? throwable.getMessage() : ExceptionUtils.getRootCause(throwable).getMessage();
    }

    private BuildResult run(Action<GradleExecutionResult> resultVerification) {
        if (projectDirectory == null) {
            throw new InvalidRunnerConfigurationException("Please specify a project directory before executing the build");
        }

        File testKitDir = createTestKitDir(testKitDirProvider);

        GradleExecutionResult execResult = gradleExecutor.run(
            gradleHome,
            testKitDir,
            projectDirectory,
            arguments,
            jvmArguments,
            classpath,
            debug
        );

        resultVerification.execute(execResult);

        return new DefaultBuildResult(
            execResult.getStandardOutput(),
            execResult.getStandardError(),
            execResult.getTasks()
        );
    }

    private File createTestKitDir(TestKitDirProvider testKitDirProvider) {
        File dir = testKitDirProvider.getDir();
        if (dir.isDirectory()) {
            if (!dir.canWrite()) {
                throw new InvalidRunnerConfigurationException("Unable to write to test kit directory: " + dir.getAbsolutePath());
            }
            return dir;
        } else if (dir.exists()) {
            throw new InvalidRunnerConfigurationException("Unable to use non-directory as test kit directory: " + dir.getAbsolutePath());
        } else if (dir.mkdirs() || dir.isDirectory()) {
            return dir;
        } else {
            throw new InvalidRunnerConfigurationException("Unable to create test kit directory: " + dir.getAbsolutePath());
        }
    }

}
