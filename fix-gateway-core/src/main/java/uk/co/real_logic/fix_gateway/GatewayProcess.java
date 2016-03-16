/*
 * Copyright 2015-2016 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway;

import uk.co.real_logic.aeron.Aeron;
import uk.co.real_logic.agrona.ErrorHandler;
import uk.co.real_logic.agrona.concurrent.*;
import uk.co.real_logic.agrona.concurrent.errors.DistinctErrorLog;
import uk.co.real_logic.fix_gateway.streams.Streams;

import java.nio.channels.ClosedByInterruptException;

import static uk.co.real_logic.aeron.driver.Configuration.ERROR_BUFFER_LENGTH_PROP_NAME;
import static uk.co.real_logic.agrona.CloseHelper.quietClose;
import static uk.co.real_logic.agrona.concurrent.AgentRunner.startOnThread;

public class GatewayProcess implements AutoCloseable
{
    public static final int INBOUND_LIBRARY_STREAM = 0;
    public static final int OUTBOUND_LIBRARY_STREAM = 1;
    public static final int OUTBOUND_REPLAY_STREAM = 2;

    protected MonitoringFile monitoringFile;
    protected FixCounters fixCounters;
    protected AgentRunner errorPrinterRunner;
    protected ErrorHandler errorHandler;
    protected DistinctErrorLog distinctErrorLog;
    protected Aeron aeron;
    protected Streams inboundLibraryStreams;
    protected Streams outboundLibraryStreams;

    protected void init(final CommonConfiguration configuration)
    {
        initMonitoring(configuration);
        initErrorPrinter(configuration);
        initAeron(configuration);
        initStreams(configuration);
    }

    private void initMonitoring(final CommonConfiguration configuration)
    {
        monitoringFile = new MonitoringFile(true, configuration);
        fixCounters = new FixCounters(monitoringFile.createCountersManager());
        final EpochClock clock = new SystemEpochClock();
        distinctErrorLog = new DistinctErrorLog(monitoringFile.errorBuffer(), clock);
        errorHandler = throwable ->
        {
            if (!distinctErrorLog.record(throwable))
            {
                System.err.println("Error Log is full, consider increasing " + ERROR_BUFFER_LENGTH_PROP_NAME);
                throwable.printStackTrace();
            }
        };
    }

    private void initErrorPrinter(final CommonConfiguration configuration)
    {
        if (configuration.printErrorMessages())
        {
            final ErrorPrinter printer = new ErrorPrinter(monitoringFile.errorBuffer());
            errorPrinterRunner = new AgentRunner(
                configuration.errorPrinterIdleStrategy(), Throwable::printStackTrace, null, printer);
        }
    }

    private void initStreams(final CommonConfiguration configuration)
    {
        final String channel = configuration.aeronChannel();
        final NanoClock nanoClock = new SystemNanoClock();

        inboundLibraryStreams = new Streams(
            channel, aeron, fixCounters.failedInboundPublications(), INBOUND_LIBRARY_STREAM, nanoClock,
            configuration.inboundMaxClaimAttempts());
        outboundLibraryStreams = new Streams(
            channel, aeron, fixCounters.failedOutboundPublications(), OUTBOUND_LIBRARY_STREAM, nanoClock,
            configuration.outboundMaxClaimAttempts());
    }

    private void initAeron(final CommonConfiguration configuration)
    {
        final Aeron.Context ctx = aeronContext(configuration);
        aeron = Aeron.connect(ctx);
    }

    private Aeron.Context aeronContext(final CommonConfiguration configuration)
    {
        final Aeron.Context ctx = configuration.aeronContext();
        ctx.errorHandler(throwable ->
        {
            if (!(throwable instanceof ClosedByInterruptException))
            {
                errorHandler.onError(throwable);
            }
        });
        return ctx;
    }

    protected void start()
    {
        if (errorPrinterRunner != null)
        {
            startOnThread(errorPrinterRunner);
        }
    }

    public void close()
    {
        quietClose(errorPrinterRunner);
        aeron.close();
        monitoringFile.close();
    }
}
