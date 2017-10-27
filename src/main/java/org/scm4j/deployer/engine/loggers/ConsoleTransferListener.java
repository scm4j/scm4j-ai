package org.scm4j.deployer.engine.loggers;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ConsoleTransferListener
        extends AbstractTransferListener
{

    private Map<TransferResource, Long> downloads = new ConcurrentHashMap<>();

    private int lastLength;

    @Override
    public void transferInitiated( TransferEvent event )
    {

        String message = event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploading" : "Downloading";

        log.info( message + ": " + event.getResource().getRepositoryUrl() + event.getResource().getResourceName() );
    }

    @Override
    public void transferProgressed( TransferEvent event )
    {
        TransferResource resource = event.getResource();
        downloads.put( resource, event.getTransferredBytes());

        StringBuilder buffer = new StringBuilder( 64 );

        for ( Map.Entry<TransferResource, Long> entry : downloads.entrySet() )
        {
            long total = entry.getKey().getContentLength();
            long complete = entry.getValue();

            buffer.append( getStatus( complete, total ) ).append( "  " );
        }

        int pad = lastLength - buffer.length();
        lastLength = buffer.length();
        pad( buffer, pad );
        buffer.append( '\r' );

        log.info( buffer.toString() );
    }

    private String getStatus( long complete, long total )
    {
        if ( total >= 1048576 )
        {
            return toMB( complete ) + "/" + toMB( total ) + " MB ";
        }
        else if ( total >= 0 )
        {
            return complete + "/" + total + " B ";
        }
        else if ( complete >= 1048576 )
        {
            return toMB( complete ) + " MB ";
        }
        else
        {
            return complete + " B ";
        }
    }

    private void pad( StringBuilder buffer, int spaces )
    {
        String block = "                                        ";
        while ( spaces > 0 )
        {
            int n = Math.min( spaces, block.length() );
            buffer.append( block, 0, n );
            spaces -= n;
        }
    }

    @Override
    public void transferSucceeded( TransferEvent event )
    {
        transferCompleted( event );

        TransferResource resource = event.getResource();
        long contentLength = event.getTransferredBytes();
        if ( contentLength >= 0 )
        {
            String type = ( event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploaded" : "Downloaded" );
            String len = contentLength >= 1048576 ? toMB( contentLength ) + " MB" : contentLength + " B";

            String throughput = "";
            long duration = System.currentTimeMillis() - resource.getTransferStartTime();
            if ( duration > 0 )
            {
                long bytes = contentLength - resource.getResumeOffset();
                DecimalFormat format = new DecimalFormat( "0.0", new DecimalFormatSymbols( Locale.ENGLISH ) );
                double kbPerSec = ( bytes / 1048576.0 ) / ( duration / 1000.0 );
                throughput = " at " + format.format( kbPerSec ) + " MB/sec";
            }

            log.info( type + ": " + resource.getRepositoryUrl() + resource.getResourceName() + " (" + len
                    + throughput + ")" );
        }
    }

    @Override
    public void transferFailed( TransferEvent event )
    {
        transferCompleted( event );

        if ( !( event.getException() instanceof MetadataNotFoundException) )
        {
            log.trace(event.getException().getMessage(),event.getException());
        }
    }

    private void transferCompleted( TransferEvent event )
    {
        downloads.remove( event.getResource() );

        StringBuilder buffer = new StringBuilder( 64 );
        pad( buffer, lastLength );
        buffer.append( '\r' );
        log.info( buffer.toString() );
    }

    public void transferCorrupted( TransferEvent event )
    {
        log.trace(event.getException().getMessage(),event.getException());
    }

    protected long toMB(long bytes )
    {
        return ( bytes + 1023 ) / 1048576;
    }

}
