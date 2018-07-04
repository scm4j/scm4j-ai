package org.scm4j.deployer.engine.loggers;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TransferListener
		extends AbstractTransferListener {
	private static final int MB = 1048576;
	private static final int KB = 102400;
	private final Map<TransferResource, Long> downloads = new ConcurrentHashMap<>();
	private int lastLength;
	private String record;

	@Override
	public void transferProgressed(TransferEvent event) {
		TransferResource resource = event.getResource();
		downloads.put(resource, event.getTransferredBytes());

		StringBuilder buffer = new StringBuilder(64);

		for (Map.Entry<TransferResource, Long> entry : downloads.entrySet()) {
			long total = entry.getKey().getContentLength();
			long complete = entry.getValue();

			if (toMB(total) > 1)
				buffer.append(getStatus(complete, total)).append("  ");
		}

		int pad = lastLength - buffer.length();
		lastLength = buffer.length();
		pad(buffer, pad);
		buffer.append('\r');
		//TODO if Mb will be needed
//		if (record != null && !record.equals(buffer.toString()) && !event.getResource().getResourceName()
//				.endsWith("pom"))
//			log.info(buffer.toString());

		record = buffer.toString();
	}

	private String getStatus(long complete, long total) {
		if (total >= MB) {
			return toMB(complete) + "/" + toMB(total) + " MB ";
		} else if (total >= KB) {
			return toKB(complete) + "00/" + toKB(total) + "00 KB ";
		} else if (complete >= MB) {
			return toMB(complete) + " MB ";
		} else {
			return toKB(complete) + "00 KB ";
		}
	}

	private void pad(StringBuilder buffer, int spaces) {
		String block = "                                        ";
		while (spaces > 0) {
			int n = Math.min(spaces, block.length());
			buffer.append(block, 0, n);
			spaces -= n;
		}
	}

	@Override
	public void transferFailed(TransferEvent event) {
		if (!(event.getException() instanceof MetadataNotFoundException)) {
			if (log.isDebugEnabled())
				log.debug(event.getException().getMessage(), event.getException());
		}
	}

	public void transferCorrupted(TransferEvent event) {
		if (log.isDebugEnabled())
			log.debug(event.getException().getMessage(), event.getException());
	}

	private long toMB(long bytes) {
		return (bytes + (KB - 1)) / MB;
	}

	private long toKB(long bytes) {
		return (bytes + (KB - 1)) / KB;
	}

}
