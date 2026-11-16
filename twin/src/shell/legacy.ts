/**
 * Temporary projection for the shipped shell. Document units are canonical;
 * the old shell still renders a 338-wide artboard until Task 5 removes it.
 */
export const LEGACY_UNIT = 338 / 765;

export const toLegacy = (doc: number) => doc * LEGACY_UNIT;
export const fromLegacy = (css: number) => css / LEGACY_UNIT;
