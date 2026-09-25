/*
 * This file is part of EverFurnace.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Licensed under the MIT License. See LICENSE.txt in the project root
 * for the full license text.
 *
 * SPDX-License-Identifier: MIT
 */
package mod.gottsch.neoforge.everfurnace.core.util;

/**
 * Immutable result returned by {@code applyCookTime()}.
 *
 * @param itemsCooked number of items fully smelted during this catch-up pass
 * @param xpEarned    total XP earned during this catch-up pass (may be fractional)
 *
 * @author by Mark Gottschling on 3/31/2026
 */
public record CookResult(int itemsCooked, float xpEarned) {}
