package com.rynekryz.exiless.core

import java.io.InputStream
import java.io.OutputStream

object MetadataStripper {
    fun strip(format: Format, input: InputStream, output: OutputStream?, options: StripOptions = StripOptions()): StripReport {
        val reader = Reader(input)
        val sink = Sink(output)
        val removed = when (format) {
            Format.JPEG -> JpegStripper.run(reader, sink, options)
            Format.PNG -> PngStripper.run(reader, sink, options)
            Format.WEBP -> WebpStripper.run(reader, sink, options)
            Format.GIF -> GifStripper.run(reader, sink)
            Format.HEIF -> HeifStripper.run(reader, sink)
            Format.MP4 -> Mp4Stripper.run(reader, sink, options)
        }
        sink.flush()
        return StripReport(removed)
    }
}
