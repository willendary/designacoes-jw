package br.com.willendary.designacoesjw.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import javax.imageio.ImageIO

internal val DesktopPublicationImageDecoder: (ByteArray) -> ImageBitmap? = { bytes ->
    ImageIO.createImageInputStream(bytes.inputStream()).use { input ->
        val readers = ImageIO.getImageReaders(input)
        if (!readers.hasNext()) null else {
            val reader = readers.next()
            try {
                reader.input = input
                val sample = ((maxOf(reader.getWidth(0), reader.getHeight(0)) + 1599) / 1600).coerceAtLeast(1)
                val params = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                reader.read(0, params).toComposeImageBitmap()
            } finally { reader.dispose() }
        }
    }
}
