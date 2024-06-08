package com.aeltumn.realms.crossfire.feature

import com.aeltumn.realms.common.tick
import com.aeltumn.realms.crossfire.References
import com.aeltumn.realms.crossfire.component.CrossfireScoreboards
import com.aeltumn.realms.crossfire.component.CrossfireTags
import io.github.ayfri.kore.DataPack
import io.github.ayfri.kore.arguments.enums.Gamemode
import io.github.ayfri.kore.arguments.maths.vec3
import io.github.ayfri.kore.arguments.numbers.localPos
import io.github.ayfri.kore.arguments.numbers.ranges.rangeOrInt
import io.github.ayfri.kore.arguments.scores.score
import io.github.ayfri.kore.arguments.selector.scores
import io.github.ayfri.kore.arguments.types.literals.allEntities
import io.github.ayfri.kore.arguments.types.literals.allPlayers
import io.github.ayfri.kore.arguments.types.literals.literal
import io.github.ayfri.kore.arguments.types.literals.self
import io.github.ayfri.kore.commands.effect
import io.github.ayfri.kore.commands.execute.Anchor
import io.github.ayfri.kore.commands.execute.execute
import io.github.ayfri.kore.commands.function
import io.github.ayfri.kore.commands.gamemode
import io.github.ayfri.kore.commands.spectate
import io.github.ayfri.kore.commands.tag
import io.github.ayfri.kore.commands.tp
import io.github.ayfri.kore.functions.function
import io.github.ayfri.kore.generated.Effects

/** Sets up spectating. */
public object Spectating {

    /** The name of the start spectating function. */
    public const val ENTER_SPECTATING_FUNCTION: String = "start_spectating"

    /** Configures spectating. */
    public fun configure(dataPack: DataPack) {
        dataPack.apply {
            tick("remove_spectating_tags") {
                for (playerIndex in 0 until References.PLAYER_COUNT) {
                    // Remove the player spectate tag if that player is now also spectating
                    execute {
                        // Find anyone spectating player index
                        asTarget(
                            allPlayers {
                                tag = "${CrossfireTags.SPECTATE_PLAYER}-$playerIndex"
                                tag = CrossfireTags.SPECTATING
                            }
                        )

                        // Unless someone is playing with that index
                        unlessCondition {
                            entity(
                                allPlayers {
                                    tag = "${CrossfireTags.PLAYER}-$playerIndex"
                                    tag = !CrossfireTags.SPECTATING
                                }
                            )
                        }

                        // Make them no longer spectate them
                        run {
                            // Remove spectating tag
                            tag(self()) {
                                remove("${CrossfireTags.SPECTATE_PLAYER}-$playerIndex")
                            }

                            // Set game mode back to adventure (we only let you be in spectator while spectating someone)
                            gamemode(Gamemode.ADVENTURE, self())
                        }
                    }

                    // Make them spectate this player when possible
                    execute {
                        // Find anyone spectating player index that are in spectator mode
                        asTarget(
                            allPlayers {
                                tag = "${CrossfireTags.SPECTATE_PLAYER}-$playerIndex"
                                tag = CrossfireTags.SPECTATING
                                gamemode = Gamemode.SPECTATOR
                            }
                        )

                        // Make them spectate the target
                        run {
                            spectate(allEntities(true) {
                                tag = "${CrossfireTags.PLAYER}-$playerIndex"
                                tag = !CrossfireTags.SPECTATING
                            }, self())
                        }
                    }
                }
            }

            for ((index, map) in References.MAPS.withIndex()) {
                tick("check_spectating-$map") {
                    // Start spectating if applicable
                    execute {
                        ifCondition {
                            score(literal(map), CrossfireScoreboards.STARTED, rangeOrInt(0))
                        }
                        asTarget(
                            allPlayers {
                                tag = !"${CrossfireTags.JOINED}-${map}"
                                tag = !CrossfireTags.ADMIN
                                tag = !CrossfireTags.SPECTATING

                                scores {
                                    score(CrossfireScoreboards.TARGET_MAP_INDEX) equalTo index
                                }
                            }
                        )

                        run {
                            function(References.NAMESPACE, "${ENTER_SPECTATING_FUNCTION}-$map")
                        }
                    }

                    // Teleport them to the flight path of this map
                    execute {
                        // Find all spectators in adventure mode on this map
                        asTarget(
                            allPlayers {
                                gamemode = Gamemode.ADVENTURE
                                tag = CrossfireTags.SPECTATING

                                scores {
                                    score(CrossfireScoreboards.TARGET_MAP_INDEX) equalTo index
                                }
                            }
                        )

                        // Find the flight path marker for this map
                        at(allEntities(true) {
                            tag = "flightpath${index}"
                        })

                        // Teleport them to face the target
                        run {
                            tp(
                                self(),
                                vec3(0.0.localPos, 0.0.localPos, 18.0.localPos),
                                allEntities(true) {
                                    tag = "spectatetarget${index}"
                                },
                                Anchor.EYES,
                            )
                        }
                    }
                }

                function("${ENTER_SPECTATING_FUNCTION}-$map") {
                    // Put them in the spectator system
                    effect(self()) {
                        giveInfinite(Effects.LEVITATION, 255, true)
                        giveInfinite(Effects.INVISIBILITY, 255, true)
                    }

                    // Make them be in spectator mode if they have a target already,
                    // otherwise keep them in adventure mode
                    execute {
                        unlessCondition {
                            entity(
                                self {
                                    for (playerIndex in 0 until References.PLAYER_COUNT) {
                                        tag = !"${CrossfireTags.PLAYER}-$playerIndex"
                                    }
                                }
                            )
                        }
                        run {
                            gamemode(Gamemode.SPECTATOR, self())
                        }
                    }

                    // Give the player the spectating tag last so the effects don't activate early
                    tag(self()) {
                        add(CrossfireTags.SPECTATING)
                    }
                }
            }
        }
    }
}