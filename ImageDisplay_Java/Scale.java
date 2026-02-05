import java.awt.image.BufferedImage;

public class Scale {

	/**
	 * Scale down an image using a simple 3x3 averaging (anti-alias) kernel.
	 * If scale == 1.0, returns the original image reference.
	 */
	public static BufferedImage scaleImage(BufferedImage src, float scale) {
		if (scale <= 0f) throw new IllegalArgumentException("scale must be > 0");
		if (scale == 1.0f) return src;

		int srcW = src.getWidth();
		int srcH = src.getHeight();
		int dstW = Math.max(1, Math.round(srcW * scale));
		int dstH = Math.max(1, Math.round(srcH * scale));

		BufferedImage dst = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_RGB);

		for (int y = 0; y < dstH; y++) {
			for (int x = 0; x < dstW; x++) {
				// Map destination pixel to source coordinate
				float srcXf = x / scale;
				float srcYf = y / scale;
				int srcX = Math.round(srcXf);
				int srcY = Math.round(srcYf);

				int rSum = 0, gSum = 0, bSum = 0, cnt = 0;

				// 3x3 averaging kernel centered at (srcX,srcY)
				for (int ky = srcY - 1; ky <= srcY + 1; ky++) {
					if (ky < 0 || ky >= srcH) continue;
					for (int kx = srcX - 1; kx <= srcX + 1; kx++) {
						if (kx < 0 || kx >= srcW) continue;
						int pix = src.getRGB(kx, ky);
						int r = (pix >> 16) & 0xff;
						int g = (pix >> 8) & 0xff;
						int b = pix & 0xff;
						rSum += r; gSum += g; bSum += b; cnt++;
					}
				}

				if (cnt == 0) cnt = 1;
				int rAvg = (rSum + cnt/2) / cnt;
				int gAvg = (gSum + cnt/2) / cnt;
				int bAvg = (bSum + cnt/2) / cnt;

				int outPix = (0xff << 24) | (rAvg << 16) | (gAvg << 8) | bAvg;
				dst.setRGB(x, y, outPix);
			}
		}

		return dst;
	}
}
