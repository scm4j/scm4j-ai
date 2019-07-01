package org.scm4j.deployer.engine.loggers;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TransferListener
		extends AbstractTransferListener {
	private static final int MB = 1048576;
	private final Map<TransferResource, Long> downloads = new ConcurrentHashMap<>();
	private String record;

	@Override
	public void transferProgressed(TransferEvent event) {
		TransferResource resource = event.getResource();
		downloads.put(resource, event.getTransferredBytes());

		StringBuilder buffer = new StringBuilder(64);

		for (Map.Entry<TransferResource, Long> entry : downloads.entrySet()) {
			long total = entry.getKey().getContentLength();
			long complete = entry.getValue();

			if (toMB(total) > 50) {
				long result = getStatus(complete, total);
				if (result % 10 == 0) {
					buffer.append(result);
				}
			}
		}

		if (record != null && !record.equals(buffer.toString()) && !event.getResource().getResourceName()
				.endsWith("pom") && !buffer.toString().isEmpty())
			log.info("Downloading " + StringUtils.substringAfterLast(event.getResource().getResourceName(), "/")
					+ " (" + buffer.toString() + " % completed)");
		record = buffer.toString();
	}

	private long getStatus(long complete, long total) {
		return (toMB(complete) * 100) / toMB(total);
	}

	private long toMB(long bytes) {
		return bytes / MB;
	}

}
