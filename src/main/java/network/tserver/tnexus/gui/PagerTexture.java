package network.tserver.tnexus.gui;

/**
 * Stores enabled and disabled Base64 head textures for a pager button.
 *
 * @param enabledTexture enabled-state texture
 * @param disabledTexture disabled-state texture
 */
public record PagerTexture(String enabledTexture, String disabledTexture) {
}
