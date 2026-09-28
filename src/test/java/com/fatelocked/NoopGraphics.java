package com.fatelocked;

import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;

/**
 * A surface that draws nothing and counts what it is asked to draw (A9), so a test can measure
 * the plugin's own drawing loop without Java2D's work, or its garbage, in the count. It keeps
 * what it is handed, as a real surface does, so the JIT can't do away with an object made for
 * it and hide the garbage.
 */
final class NoopGraphics extends Graphics2D
{
    int fills;
    int draws;
    private Shape shape;
    private Shape clip;
    private Color color;
    private Stroke stroke;

    @Override
    public void fillRect(int x, int y, int width, int height)
    {
        fills++;
    }

    @Override
    public void draw(Shape s)
    {
        draws++;
        shape = s;
    }

    @Override
    public void fill(Shape s)
    {
        fills++;
        shape = s;
    }

    // Everything else does nothing.

    @Override
    public void drawString(String str, int x, int y)
    {
    }

    @Override
    public void drawString(String str, float x, float y)
    {
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, int x, int y)
    {
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, float x, float y)
    {
    }

    @Override
    public void drawGlyphVector(GlyphVector g, float x, float y)
    {
    }

    @Override
    public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs)
    {
        return true;
    }

    @Override
    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y)
    {
    }

    @Override
    public void drawRenderedImage(RenderedImage img, AffineTransform xform)
    {
    }

    @Override
    public void drawRenderableImage(RenderableImage img, AffineTransform xform)
    {
    }

    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver observer)
    {
        return true;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer)
    {
        return true;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer)
    {
        return true;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer)
    {
        return true;
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
        ImageObserver observer)
    {
        return true;
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
        Color bgcolor, ImageObserver observer)
    {
        return true;
    }

    @Override
    public boolean hit(Rectangle rect, Shape s, boolean onStroke)
    {
        return false;
    }

    @Override
    public GraphicsConfiguration getDeviceConfiguration()
    {
        return null;
    }

    @Override
    public void setComposite(Composite comp)
    {
    }

    @Override
    public void setPaint(Paint paint)
    {
    }

    @Override
    public void setStroke(Stroke s)
    {
        stroke = s;
    }

    @Override
    public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue)
    {
    }

    @Override
    public Object getRenderingHint(RenderingHints.Key hintKey)
    {
        return null;
    }

    @Override
    public void setRenderingHints(Map<?, ?> hints)
    {
    }

    @Override
    public void addRenderingHints(Map<?, ?> hints)
    {
    }

    @Override
    public RenderingHints getRenderingHints()
    {
        return null;
    }

    @Override
    public void translate(int x, int y)
    {
    }

    @Override
    public void translate(double tx, double ty)
    {
    }

    @Override
    public void rotate(double theta)
    {
    }

    @Override
    public void rotate(double theta, double x, double y)
    {
    }

    @Override
    public void scale(double sx, double sy)
    {
    }

    @Override
    public void shear(double shx, double shy)
    {
    }

    @Override
    public void transform(AffineTransform tx)
    {
    }

    @Override
    public void setTransform(AffineTransform tx)
    {
    }

    @Override
    public AffineTransform getTransform()
    {
        return null;
    }

    @Override
    public Paint getPaint()
    {
        return null;
    }

    @Override
    public Composite getComposite()
    {
        return null;
    }

    @Override
    public void setBackground(Color color)
    {
    }

    @Override
    public Color getBackground()
    {
        return null;
    }

    @Override
    public Stroke getStroke()
    {
        return stroke;
    }

    @Override
    public void clip(Shape s)
    {
        clip = s;
    }

    @Override
    public FontRenderContext getFontRenderContext()
    {
        return null;
    }

    @Override
    public Graphics create()
    {
        return this;
    }

    @Override
    public Color getColor()
    {
        return color;
    }

    @Override
    public void setColor(Color c)
    {
        color = c;
    }

    @Override
    public void setPaintMode()
    {
    }

    @Override
    public void setXORMode(Color c1)
    {
    }

    @Override
    public Font getFont()
    {
        return null;
    }

    @Override
    public void setFont(Font font)
    {
    }

    @Override
    public FontMetrics getFontMetrics(Font f)
    {
        return null;
    }

    @Override
    public Rectangle getClipBounds()
    {
        return null;
    }

    @Override
    public void clipRect(int x, int y, int width, int height)
    {
    }

    @Override
    public void setClip(int x, int y, int width, int height)
    {
    }

    @Override
    public Shape getClip()
    {
        return clip;
    }

    @Override
    public void setClip(Shape clip)
    {
        this.clip = clip;
    }

    @Override
    public void copyArea(int x, int y, int width, int height, int dx, int dy)
    {
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2)
    {
    }

    @Override
    public void clearRect(int x, int y, int width, int height)
    {
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight)
    {
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight)
    {
    }

    @Override
    public void drawOval(int x, int y, int width, int height)
    {
    }

    @Override
    public void fillOval(int x, int y, int width, int height)
    {
    }

    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle)
    {
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle)
    {
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints)
    {
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints)
    {
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints)
    {
    }

    @Override
    public void dispose()
    {
    }
}
