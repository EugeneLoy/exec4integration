package org.exec4integration.maven.plugin;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.util.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.IOUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

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

    @Parameter( property = "exec4integration.outputFile" )
    private File outputFile;

    public void execute() throws MojoExecutionException {
        Executor executor = new DefaultExecutor();

        // prepare command line
        CommandLine command = new CommandLine(executable);
        if (hasArguments()) {
            command.addArguments(arguments);
        }

        // setup output redirection
        PumpStreamHandler streamHandler = null;
        OutputStream outputStream = null;
        if (outputFile != null) {
            try {
                outputStream = new FileOutputStream(outputFile);
            } catch (FileNotFoundException e) {
                // TODO fix error message
                throw new MojoExecutionException("Failed to write to output file", e);
            }
            streamHandler = new PumpStreamHandler(new BufferedOutputStream(outputStream));
        } else {
            streamHandler = new PumpStreamHandler(new BufferedOutputStream(System.out), new BufferedOutputStream(System.err), System.in);
        }
        executor.setStreamHandler(streamHandler);

        // do actual run
        try {
            // TODO ugly hack to simply "make it work". Fix this ASAP.
            final OutputStream outputStream1 = outputStream;
            final PumpStreamHandler streamHandler1 = streamHandler;
            ExecuteWatchdog watchdog = new ExecuteWatchdog(60 * 60 * 1000) {
                @Override
                public synchronized void start(final Process processToMonitor) {
                    super.start(processToMonitor);
                    Runtime.getRuntime().addShutdownHook(new Thread() {
                        @Override
                        public void run() {
                            processToMonitor.destroy();
                            try {
                                streamHandler1.stop();
                            } catch (IOException e) {
                            }
                            IOUtil.close(outputStream1);
                        }
                    });
                }
            };
            executor.setWatchdog(watchdog);

            //
            streamHandler.start();

            getLog().info("Running: " + StringUtils.toString(command.toStrings(), " "));
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
