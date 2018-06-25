package org.insanedevelopment.nx.xci.cutter.backend;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ObservableInputStream;
import org.apache.commons.io.input.PercentageCalculatingInputStreamObserver;
import org.insanedevelopment.nx.xci.cutter.backend.model.XciFileInformation;

public class XciFileSplitter {

	private static long SPLIT_FILE_SIZE_4GB = 4_294_934_528L;

	public static void splitAndTrimFile(XciFileInformation source, String firstTarget, WorkflowStepPercentagObserver calleeObserver) throws IOException {
		splitAndTrimFile(source, firstTarget, SPLIT_FILE_SIZE_4GB, calleeObserver);
	}

	private static void splitAndTrimFile(XciFileInformation source, String firstTarget, long chunkSize, WorkflowStepPercentagObserver calleeObserver) throws IOException {
		if (source.isSplit() || source.getCartSizeInBytes() == 0) {
			return;
		}

		if (!checkPadding(source, calleeObserver)) {
			return;
		}

		calleeObserver.setWorkflowStep(WorkflowStep.TRIMMING_AND_SPLITTING);
		String baseOutputFileName = FilenameUtils.getFullPath(firstTarget) + FilenameUtils.getBaseName(firstTarget);
		PercentageCalculatingInputStreamObserver observer = new PercentageCalculatingInputStreamObserver(source.getDataSizeInBytes(), calleeObserver);
		try (ObservableInputStream inputStream = new ObservableInputStream(FileUtils.openInputStream(new File(source.getMainFileName())))) {
			inputStream.add(observer);

			long remainingDataSize = source.getDataSizeInBytes();
			int counter = 0;
			long lastCopyCount = 0;
			long amountToCopy = Math.min(remainingDataSize, chunkSize);
			do {
				File targetFile = new File(baseOutputFileName + ".xc" + counter);
				FileUtils.forceMkdirParent(targetFile);
				try (OutputStream outputStream = FileUtils.openOutputStream(targetFile)) {
					lastCopyCount = IOUtils.copyLarge(inputStream, outputStream, 0, amountToCopy);
				}

				remainingDataSize = remainingDataSize - lastCopyCount;
				counter++;
				amountToCopy = Math.min(remainingDataSize, chunkSize);
			} while (lastCopyCount == chunkSize);

		}
	}

	private static boolean checkPadding(XciFileInformation source, WorkflowStepPercentagObserver calleeObserver) throws IOException {
		calleeObserver.setWorkflowStep(WorkflowStep.CHECK_PADDING);
		PercentageCalculatingInputStreamObserver observer = new PercentageCalculatingInputStreamObserver(source.getCartSizeInBytes() - source.getDataSizeInBytes(), calleeObserver);

		try (ObservableInputStream inputStream = new ObservableInputStream(IOUtils.buffer(FileUtils.openInputStream(new File(source.getMainFileName()))))) {
			inputStream.add(observer);
			inputStream.skip(source.getDataSizeInBytes());
			int readBytes;
			byte[] buffer = new byte[4 * 1024];
			while (IOUtils.EOF != (readBytes = inputStream.read(buffer))) {
				for (int i = 0; i < readBytes; i++) {
					if (buffer[i] != (byte) 0xFF) {
						return false;
					}
				}
			}
			return true;
		}
	}
}
