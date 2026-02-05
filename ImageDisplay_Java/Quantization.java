import java.awt.image.BufferedImage;
import java.util.Arrays;

public class Quantization {

	public static BufferedImage quantizeImage(BufferedImage src, int Q, int M) {
		if (Q <= 0) return src;
		int bitsPerChannel = Q / 3;
		if (bitsPerChannel <= 0) return src;

		int K = 1 << bitsPerChannel;

		// Build centers depending on mode M
		int[] centers = null;
		if (M == 256) {
			// Will compute per-channel centers via optimal (k-means) below
			// but for initialization we set null to indicate per-channel handling
			centers = null;
		} else if (M == -1) {
			centers = uniformCenters(K);
		} else {
			centers = logarithmicCenters(K, M);
		}

		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

		if (M == 256) {
			// Optimal: compute histograms and centers per channel
			int[] histR = new int[256];
			int[] histG = new int[256];
			int[] histB = new int[256];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int p = src.getRGB(x, y);
					histR[(p >> 16) & 0xff]++;
					histG[(p >> 8) & 0xff]++;
					histB[p & 0xff]++;
				}
			}

			int[] centersR = optimalCenters(histR, K);
			int[] centersG = optimalCenters(histG, K);
			int[] centersB = optimalCenters(histB, K);

			// Map pixels
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int p = src.getRGB(x, y);
					int r = (p >> 16) & 0xff;
					int g = (p >> 8) & 0xff;
					int b = p & 0xff;
					int rq = nearestCenter(r, centersR);
					int gq = nearestCenter(g, centersG);
					int bq = nearestCenter(b, centersB);
					int out = (0xff << 24) | (rq << 16) | (gq << 8) | bq;
					dst.setRGB(x, y, out);
				}
			}
		} else {
			// Uniform or logarithmic using same centers for all channels
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int p = src.getRGB(x, y);
					int r = (p >> 16) & 0xff;
					int g = (p >> 8) & 0xff;
					int b = p & 0xff;
					int rq = centers[ r * centers.length / 256 >= centers.length ? centers.length-1 : (r * centers.length / 256)];
					int gq = centers[ g * centers.length / 256 >= centers.length ? centers.length-1 : (g * centers.length / 256)];
					int bq = centers[ b * centers.length / 256 >= centers.length ? centers.length-1 : (b * centers.length / 256)];
					int out = (0xff << 24) | (rq << 16) | (gq << 8) | bq;
					dst.setRGB(x, y, out);
				}
			}
		}

		return dst;
	}

	private static int[] uniformCenters(int K) {
		int[] c = new int[K];
		double w = 256.0 / K;
		for (int i = 0; i < K; i++) {
			int center = (int) Math.round((i + 0.5) * w - 0.5);
			c[i] = Math.max(0, Math.min(255, center));
		}
		return c;
	}

	private static int[] logarithmicCenters(int K, int M) {
		int[] c = new int[K];
		// Use a simple exponential spacing parameterized by M
		double lambda = 1.0 + (M / 64.0); // M=0 -> lambda=1 (near linear). larger M -> more bias
		if (lambda <= 1.0) {
			return uniformCenters(K);
		}
		double denom = Math.pow(lambda, K) - 1.0;
		for (int i = 0; i < K; i++) {
			double val = (Math.pow(lambda, i) - 1.0) / (denom == 0 ? 1.0 : denom);
			int center = (int) Math.round(val * 255.0);
			c[i] = Math.max(0, Math.min(255, center));
		}
		// ensure monotonic non-decreasing and spread to full range
		if (c[0] > 0) c[0] = 0;
		c[K-1] = 255;
		for (int i = 1; i < K; i++) if (c[i] < c[i-1]) c[i] = c[i-1];
		return c;
	}

	private static int[] optimalCenters(int[] hist, int K) {
		// 1D k-means (Lloyd) on intensity histogram
		double[] centers = new double[K];
		int total = 0;
		for (int v = 0; v < 256; v++) total += hist[v];
		// initialize centers uniformly over 0..255
		for (int i = 0; i < K; i++) centers[i] = (255.0 * (i + 0.5)) / K;

		int[] labels = new int[256];
		for (int iter = 0; iter < 100; iter++) {
			boolean changed = false;
			// assignment
			for (int v = 0; v < 256; v++) {
				double bestD = Double.MAX_VALUE; int best = 0;
				for (int k = 0; k < K; k++) {
					double d = Math.abs(v - centers[k]);
					if (d < bestD) { bestD = d; best = k; }
				}
				if (labels[v] != best) { labels[v] = best; changed = true; }
			}
			// update
			double[] sum = new double[K];
			int[] count = new int[K];
			for (int v = 0; v < 256; v++) {
				int k = labels[v];
				sum[k] += hist[v] * v;
				count[k] += hist[v];
			}
			boolean anyMoved = false;
			for (int k = 0; k < K; k++) {
				double newC = (count[k] > 0) ? (sum[k] / count[k]) : centers[k];
				if (Math.abs(newC - centers[k]) > 1e-3) anyMoved = true;
				centers[k] = newC;
			}
			if (!changed && !anyMoved) break;
		}

		int[] out = new int[K];
		for (int k = 0; k < K; k++) {
			int val = (int) Math.round(centers[k]);
			out[k] = Math.max(0, Math.min(255, val));
		}
		Arrays.sort(out);
		// ensure unique and monotonic
		for (int k = 1; k < K; k++) if (out[k] <= out[k-1]) out[k] = Math.min(255, out[k-1] + 1);
		return out;
	}

	private static int nearestCenter(int v, int[] centers) {
		int best = centers[0];
		int bestD = Math.abs(v - best);
		for (int i = 1; i < centers.length; i++) {
			int d = Math.abs(v - centers[i]);
			if (d < bestD) { bestD = d; best = centers[i]; }
		}
		return best;
	}
}
