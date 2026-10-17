package io.github.itsarcn.cozmetics.cosmetic;

/**
 * Defines which player anchor or location a cosmetic attaches to.
 */
public enum AttachmentSlot {
	/**
	 * Independent companion hovering by player shoulder (e.g. pets).
	 */
	PET,
	/**
	 * Attached to player torso/body (e.g. wings, back accessories, auras).
	 */
	BODY,
	/**
	 * Attached to player head (e.g. hats, face masks).
	 */
	HEAD,
	/**
	 * Attached to player left and right arms (e.g. fists, gauntlets).
	 */
	ARMS,
	/**
	 * Attached to player left and right legs (e.g. boots, shoes).
	 */
	LEGS
}

