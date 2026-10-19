package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * An image from a resource pack texture ({@link Ui#image(String)}) or an https link ({@link Ui#webImage(String)}).
 * A panel holds at most {@link Limits#IMAGES_PER_PANEL} images.
 */
public final class Image extends Element<Image> {

    /** How the image fills its box. The client default is {@link #FILL}. */
    public enum Fit {
        /** Stretched to the whole box. */
        FILL,
        /** Scaled to fit inside the box, keeping its proportions. */
        CONTAIN,
        /** Scaled to cover the whole box, keeping its proportions. */
        COVER
    }

    /** Width and height in pixels the client uses when no size is set. */
    public static final int DEFAULT_SIZE = 64;

    private final String texture;
    private final String url;
    private Fit fit;
    private String tint;

    private Image(String texture, String url) {
        this.texture = texture;
        this.url = url;
    }

    static Image texture(String texture) {
        return new Image(Check.pattern(texture, Check.TEXTURE, "image.src.texture", "a texture such as myserver:textures/gui/banner.png"), null);
    }

    static Image web(String url) {
        Check.chars(url, 512, "image.src.url");
        if (!url.toLowerCase(Locale.ROOT).startsWith("https://")) throw Check.fail("image.src.url", "only https links");
        return new Image(null, url);
    }

    @Override
    String type() {
        return "image";
    }

    /**
     * Sets how the image fills its box.
     *
     * @param fit the fit, or null for the client default
     * @return this element
     */
    public Image fit(Fit fit) {
        this.fit = fit;
        return this;
    }

    /**
     * Sets a color the image is tinted with.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this element
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Image tint(String color) {
        this.tint = Check.color(color, "image.tint");
        return this;
    }

    @Override
    void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
        enc.image(path);
        JsonObject src = new JsonObject();
        Json.str(src, "texture", texture);
        Json.str(src, "url", url);
        o.add("src", src);
        if (fit != null) o.addProperty("fit", Check.lower(fit));
        Json.str(o, "tint", tint);
    }
}
