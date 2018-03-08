package com.ecb.tamochu.tool.phashjoiner.util;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageProducer;
import java.awt.image.IndexColorModel;
import java.util.ArrayList;
import java.util.List;

import javax.swing.GrayFilter;

public class DHashImageUtil {
	private static final int DHASH_WIDTH = 9;
	private static final int DHASH_HEIGHT = 8;
	private static final int DHASH_GRAYSCALE_P = 50;
	private static final int HAMMING_DIST_COMPARE_THRESHOLD = 10;

	public static BufferedImage rescale(BufferedImage src, int w, int h) {
		BufferedImage dst = null;
		if (src == null) {
			return null;
		}
		if (src.getColorModel() instanceof IndexColorModel) {
			dst = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR_PRE);
		} else {
			dst = new BufferedImage(w, h, src.getType());
		}

		double sx = (double) w / src.getWidth();
		double sy = (double) h / src.getHeight();
		AffineTransform trans = AffineTransform.getScaleInstance(sx, sy);

		if (dst.getColorModel().hasAlpha() && dst.getColorModel() instanceof IndexColorModel) {
			int transparentPixel = ((IndexColorModel) dst.getColorModel()).getTransparentPixel();
			for (int i = 0; i < dst.getWidth(); i++) {
				for (int j = 0; j < dst.getHeight(); j++) {
					dst.setRGB(i, j, transparentPixel);
				}
			}
		}

		Graphics2D g = dst.createGraphics();
		g.drawImage(src, trans, null);
		g.dispose();

		return dst;
	}

	public static BufferedImage convertGrayscale(BufferedImage src, int p) {
		ImageFilter filter = new GrayFilter(true, p);
		ImageProducer producer = new FilteredImageSource(src.getSource(), filter);
		Image img = Toolkit.getDefaultToolkit().createImage(producer);
		return imageToBufferedImage(img);
	}

	public static BufferedImage imageToBufferedImage(Image img) {
		if (img instanceof BufferedImage) {
			return (BufferedImage) img;
		}

		BufferedImage ret = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_4BYTE_ABGR_PRE);
		Graphics2D g = ret.createGraphics();
		g.drawImage(img, 0, 0, null);
		g.dispose();

		return ret;
	}

	public static long calcDHash(BufferedImage img) {
		BufferedImage work = convertGrayscale(rescale(img, DHASH_WIDTH, DHASH_HEIGHT), DHASH_GRAYSCALE_P);
		long dHash = 0;
		for (int j = 0; j < DHASH_HEIGHT; j++) {
			for (int i = 0; i < DHASH_WIDTH - 1; i++) {
				int a = work.getRGB(i, j);
				int b = work.getRGB(i + 1, j);
				dHash = dHash << 1;
				if (a > b) {
					dHash = dHash | 1;
				}
			}
		}
		return dHash;
	}

	public static int calcHammingDist(long a, long b) {
		int dist = 0;
		long sub = a ^ b;
		while (sub != 0) {
			sub &= sub - 1;
			dist++;
		}
		return dist;
	}

	public static boolean compareImages(BufferedImage a, BufferedImage b) {
		long la = calcDHash(a);
		long lb = calcDHash(b);
		int hammingDist = calcHammingDist(la, lb);

		return hammingDist <= HAMMING_DIST_COMPARE_THRESHOLD;
	}

	public static int topDownCompare(BufferedImage base, BufferedImage piece) {
		int matchHeight = piece.getHeight();
		while (matchHeight > 0) {
			BufferedImage baseBottom = base.getSubimage(0, base.getHeight() - matchHeight, base.getWidth(), matchHeight);
			BufferedImage pieceTop = piece.getSubimage(0, 0, piece.getWidth(), matchHeight);
			if (compareImages(baseBottom, pieceTop)) {
				return matchHeight;
			}
			matchHeight--;
		}
		return -1;
	}

	public static int leftToRightCompare(BufferedImage base, BufferedImage piece) {
		int matchWidth = piece.getWidth();
		while (matchWidth >= DHASH_HEIGHT) {
			BufferedImage baseRight = base.getSubimage(base.getWidth() - matchWidth, 0, matchWidth, base.getHeight());
			BufferedImage pieceLeft = piece.getSubimage(0, 0, matchWidth, piece.getHeight());
			if (compareImages(baseRight, pieceLeft)) {
				return matchWidth;
			}
			matchWidth--;
		}
		return 0;
	}

	public static BufferedImage trimDuplicate(BufferedImage base, BufferedImage piece, boolean isVertical) {
		if (isVertical) {
			int matchHeight = topDownCompare(base, piece);
			if (matchHeight > 0) {
				return piece.getSubimage(0, matchHeight, piece.getWidth(), piece.getHeight() - matchHeight);
			}

		} else {
			int matchWidth = leftToRightCompare(base, piece);
			if (matchWidth > 0) {
				return piece.getSubimage(matchWidth, 0, piece.getWidth() - matchWidth, piece.getHeight());
			}
		}
		return piece;
	}

	public static BufferedImage joinImages(List<BufferedImage> images, boolean isVertical) {
		if (images == null || images.size() <= 0) {
			return null;
		}
		BufferedImage base = images.get(0);
		int width = base.getWidth();
		int height = base.getHeight();
		if (isVertical) {
			height = 0;
			for (BufferedImage elm : images) {
				height += elm.getHeight();
			}
		} else {
			width = 0;
			for (BufferedImage elm : images) {
				width += elm.getWidth();
			}
		}

		BufferedImage img = new BufferedImage(width, height, base.getType());
		Graphics2D g = img.createGraphics();
		if (isVertical) {
			int h = 0;
			for (BufferedImage elm : images) {
				g.drawImage(elm, 0, h, null);
				h += elm.getHeight();
			}
		} else {
			int w = 0;
			for (BufferedImage elm : images) {
				g.drawImage(elm, w, 0, null);
				w += elm.getWidth();
			}
		}
		g.dispose();

		return img;
	}

	public static BufferedImage joinWithoutDuplicate(List<BufferedImage> images, boolean isVertical) {
		if (images == null || images.size() <= 0) {
			return null;
		}
		List<BufferedImage> pieces = new ArrayList<BufferedImage>();
		BufferedImage pre = new BufferedImage(images.get(0).getWidth(), images.get(0).getHeight(), images.get(0).getType());
		pre.setData(images.get(0).getData());
		pieces.add(images.get(0));
		int i = 1;
		while (i < images.size()) {
			BufferedImage piece = images.get(i);
			pieces.add(trimDuplicate(pre, piece, isVertical));
			pre.setData(piece.getData());
			i++;
		}
		return joinImages(pieces, isVertical);
	}

	public static BufferedImage joinWithoutDuplicate(List<BufferedImage> images) {
		if (images == null || images.size() <= 0) {
			return null;
		}
		if (images.size() == 1) {
			return images.get(0);
		}
		boolean isVertical = true;
		BufferedImage a = images.get(0);
		BufferedImage b = images.get(1);
		if (topDownCompare(a, b) <= 0 && leftToRightCompare(a, b) > 0) {
			isVertical = false;
		}
		return joinWithoutDuplicate(images, isVertical);
	}
}
