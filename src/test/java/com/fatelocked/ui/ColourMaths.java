package com.fatelocked.ui;

import java.awt.Color;

/**
 * Colour maths for the palette's tests.
 *
 * <ul>
 *   <li>Contrast is WCAG 2.x, from relative luminance.</li>
 *   <li>Colour-vision deficiency is simulated with Machado, Oliveira and Fernandes
 *       (2009) at severity 1.0, in linear RGB.</li>
 *   <li>Differences are CIEDE2000 in CIELAB (D65). About 10 or more reads as clearly
 *       different.</li>
 * </ul>
 */
final class ColourMaths
{
    enum Vision
    {
        NORMAL(null),
        PROTAN(new double[][]{
            {0.152286, 1.052583, -0.204868},
            {0.114503, 0.786281, 0.099216},
            {-0.003882, -0.048116, 1.051998}}),
        DEUTAN(new double[][]{
            {0.367322, 0.860646, -0.227968},
            {0.280085, 0.672501, 0.047413},
            {-0.011820, 0.042940, 0.968881}}),
        TRITAN(new double[][]{
            {1.255528, -0.076749, -0.178779},
            {-0.078411, 0.930809, 0.147602},
            {0.004733, 0.691367, 0.303900}});

        private final double[][] matrix;

        Vision(double[][] matrix)
        {
            this.matrix = matrix;
        }
    }

    private ColourMaths()
    {
    }

    static double contrast(Color a, Color b)
    {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** CIEDE2000 between two opaque colours as someone with {@code vision} sees them. */
    static double difference(Color a, Color b, Vision vision)
    {
        return de2000(lab(simulate(a, vision)), lab(simulate(b, vision)));
    }

    /** {@code top} (with its own alpha) over an opaque {@code bottom}. */
    static Color over(Color top, Color bottom)
    {
        return Palette.over(top, top.getAlpha(), bottom);
    }

    private static double luminance(Color c)
    {
        return 0.2126 * linear(c.getRed()) + 0.7152 * linear(c.getGreen()) + 0.0722 * linear(c.getBlue());
    }

    private static double linear(int channel)
    {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double encode(double c)
    {
        c = Math.min(1.0, Math.max(0.0, c));
        return 255.0 * (c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055);
    }

    private static double[] simulate(Color c, Vision vision)
    {
        double[] rgb = {c.getRed(), c.getGreen(), c.getBlue()};
        if (vision.matrix == null)
        {
            return rgb;
        }
        double[] lin = {linear(c.getRed()), linear(c.getGreen()), linear(c.getBlue())};
        double[] out = new double[3];
        for (int i = 0; i < 3; i++)
        {
            double sum = 0;
            for (int j = 0; j < 3; j++)
            {
                sum += vision.matrix[i][j] * lin[j];
            }
            out[i] = encode(sum);
        }
        return out;
    }

    private static double[] lab(double[] rgb)
    {
        double r = linearValue(rgb[0]);
        double g = linearValue(rgb[1]);
        double b = linearValue(rgb[2]);
        double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
        double fx = f(x);
        double fy = f(y);
        double fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double linearValue(double channel)
    {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t)
    {
        double d = 6.0 / 29;
        return t > d * d * d ? Math.cbrt(t) : t / (3 * d * d) + 4.0 / 29;
    }

    private static double de2000(double[] lab1, double[] lab2)
    {
        double l1 = lab1[0];
        double a1 = lab1[1];
        double b1 = lab1[2];
        double l2 = lab2[0];
        double a2 = lab2[1];
        double b2 = lab2[2];
        double c1 = Math.hypot(a1, b1);
        double c2 = Math.hypot(a2, b2);
        double cm = (c1 + c2) / 2;
        double g = 0.5 * (1 - Math.sqrt(Math.pow(cm, 7) / (Math.pow(cm, 7) + Math.pow(25, 7))));
        double a1p = (1 + g) * a1;
        double a2p = (1 + g) * a2;
        double c1p = Math.hypot(a1p, b1);
        double c2p = Math.hypot(a2p, b2);
        double h1p = (Math.toDegrees(Math.atan2(b1, a1p)) + 360) % 360;
        double h2p = (Math.toDegrees(Math.atan2(b2, a2p)) + 360) % 360;
        double dLp = l2 - l1;
        double dCp = c2p - c1p;
        double dhp;
        if (c1p * c2p == 0)
        {
            dhp = 0;
        }
        else if (Math.abs(h2p - h1p) <= 180)
        {
            dhp = h2p - h1p;
        }
        else if (h2p - h1p > 180)
        {
            dhp = h2p - h1p - 360;
        }
        else
        {
            dhp = h2p - h1p + 360;
        }
        double dHp = 2 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(dhp / 2));
        double lpm = (l1 + l2) / 2;
        double cpm = (c1p + c2p) / 2;
        double hpm;
        if (c1p * c2p == 0)
        {
            hpm = h1p + h2p;
        }
        else if (Math.abs(h1p - h2p) <= 180)
        {
            hpm = (h1p + h2p) / 2;
        }
        else if (h1p + h2p < 360)
        {
            hpm = (h1p + h2p + 360) / 2;
        }
        else
        {
            hpm = (h1p + h2p - 360) / 2;
        }
        double t = 1 - 0.17 * Math.cos(Math.toRadians(hpm - 30)) + 0.24 * Math.cos(Math.toRadians(2 * hpm))
            + 0.32 * Math.cos(Math.toRadians(3 * hpm + 6)) - 0.20 * Math.cos(Math.toRadians(4 * hpm - 63));
        double dth = 30 * Math.exp(-Math.pow((hpm - 275) / 25, 2));
        double rc = 2 * Math.sqrt(Math.pow(cpm, 7) / (Math.pow(cpm, 7) + Math.pow(25, 7)));
        double sl = 1 + (0.015 * Math.pow(lpm - 50, 2)) / Math.sqrt(20 + Math.pow(lpm - 50, 2));
        double sc = 1 + 0.045 * cpm;
        double sh = 1 + 0.015 * cpm * t;
        double rt = -Math.sin(Math.toRadians(2 * dth)) * rc;
        return Math.sqrt(Math.pow(dLp / sl, 2) + Math.pow(dCp / sc, 2) + Math.pow(dHp / sh, 2)
            + rt * (dCp / sc) * (dHp / sh));
    }
}
