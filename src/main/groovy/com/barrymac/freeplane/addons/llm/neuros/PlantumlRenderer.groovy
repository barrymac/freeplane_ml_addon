package com.barrymac.freeplane.addons.llm.neuros

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** One bounded, on-demand PlantUML render to PNG. */
class PlantumlRenderer {

    static final int MAX_SOURCE_CHARS = 2_000_000
    static final int MAX_OUTPUT_BYTES = 20_000_000

    static BufferedImage renderPng(String source, String command, int timeoutSeconds) {
        if (!source?.trim()) throw new IllegalArgumentException('There is no diagram to preview.')
        if (source.length() > MAX_SOURCE_CHARS) {
            throw new IllegalArgumentException('The diagram is too large to preview safely.')
        }
        if (!command?.trim()) {
            throw new IllegalArgumentException('PlantUML is not configured.')
        }

        Process process = null
        Thread outputThread
        Thread errorThread
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        ByteArrayOutputStream errors = new ByteArrayOutputStream()
        AtomicReference<Exception> readProblem = new AtomicReference<>()
        try {
            ProcessBuilder builder = new ProcessBuilder(command, '-tpng', '-pipe')
            builder.redirectErrorStream(false)
            try {
                process = builder.start()
            } catch (IOException unavailable) {
                throw new IOException('PlantUML is not available on PATH.', unavailable)
            }

            final Process running = process
            outputThread = Thread.start {
                try {
                    copyBounded(running.inputStream, output, MAX_OUTPUT_BYTES)
                } catch (Exception problem) {
                    readProblem.set(problem)
                    running.destroyForcibly()
                }
            }
            errorThread = Thread.start {
                try {
                    copyBounded(running.errorStream, errors, 1_000_000)
                } catch (Exception problem) {
                    readProblem.set(problem)
                }
            }

            try {
                process.outputStream.withWriter('UTF-8') { writer ->
                    writer.write(source)
                }
            } catch (IOException writeProblem) {
                throw new IOException('PlantUML could not read the diagram source.', writeProblem)
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
                throw new IOException('The PlantUML preview timed out.')
            }
            outputThread.join(2000)
            errorThread.join(2000)
            if (readProblem.get()) throw readProblem.get()
            if (process.exitValue() != 0) {
                String detail = errors.toString('UTF-8').trim()
                throw new IOException(detail ?: 'PlantUML could not render this diagram.')
            }

            byte[] png = output.toByteArray()
            if (!png) throw new IOException('PlantUML returned no preview image.')
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png))
            if (image == null) {
                String detail = errors.toString('UTF-8').trim()
                throw new IOException(detail ?: 'PlantUML did not return a readable image.')
            }
            return image
        } finally {
            outputThread?.join(100)
            errorThread?.join(100)
            process?.destroy()
        }
    }

    private static void copyBounded(InputStream input, OutputStream output, int cap) {
        byte[] buffer = new byte[8192]
        int total = 0
        int read
        while ((read = input.read(buffer)) >= 0) {
            if (total + read > cap) {
                throw new IOException('The preview exceeded its output limit.')
            }
            output.write(buffer, 0, read)
            total += read
        }
    }
}
