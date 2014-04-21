package org.exec4integration.maven.plugin;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "run", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class RunMojo extends AbstractMojo {

    private final class NullExecuteResultHandler implements ExecuteResultHandler {
        public void onProcessFailed(ExecuteException e) {}

        public void onProcessComplete(int exitValue) {}
    }

    @Parameter(property = "exec4integration.executable", required = true)
    private String executable;

    @Parameter(property = "exec4integration.arguments")
    private String[] arguments;

    public void execute() throws MojoExecutionException {
        Executor executor = new DefaultExecutor();

        // TODO ugly hack to simply "make it work". Fix this ASAP.
        ExecuteWatchdog watchdog = new ExecuteWatchdog(60 * 60 * 1000) {
            @Override
            public synchronized void start(final Process processToMonitor) {
                super.start(processToMonitor);
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        processToMonitor.destroy();
                    }
                });
            }
        };
        executor.setWatchdog(watchdog);

        CommandLine command = new CommandLine(executable);

        if (hasArguments()) {
            command.addArguments(arguments);
        }

        try {
            executor.execute(command, new NullExecuteResultHandler());
        } catch (Exception e) {
            // TODO fix error message
            throw new MojoExecutionException("Failed to run", e);
        }

    }

    private boolean hasArguments() {
        return arguments != null && arguments.length > 0;
    }

}
