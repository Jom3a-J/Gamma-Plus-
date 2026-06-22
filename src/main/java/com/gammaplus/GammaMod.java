package com.gammaplus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gamma Plus — Shared constants and logger holder.
 *
 * This mod is purely client-side, so there is no common initializer.
 * The client entrypoint lives in {@link GammaModClient}.
 */
public final class GammaMod {

    public static final String MOD_ID = "gammaplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private GammaMod() {}
}
