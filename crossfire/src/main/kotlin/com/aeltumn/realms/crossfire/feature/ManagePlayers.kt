package com.aeltumn.realms.crossfire.feature

import com.aeltumn.realms.common.Configurable
import com.aeltumn.realms.common.clearBossBarPlayers
import com.aeltumn.realms.common.tick
import com.aeltumn.realms.crossfire.References
import com.aeltumn.realms.crossfire.component.CrossfireBossbars
import com.aeltumn.realms.crossfire.component.CrossfireScoreboards
import com.aeltumn.realms.crossfire.component.CrossfireTags
import com.aeltumn.realms.crossfire.component.CrossfireTeams
import io.github.ayfri.kore.DataPack
import io.github.ayfri.kore.arguments.enums.ExperienceType
import io.github.ayfri.kore.arguments.enums.Gamemode
import io.github.ayfri.kore.arguments.maths.vec3
import io.github.ayfri.kore.arguments.numbers.Xp
import io.github.ayfri.kore.arguments.numbers.rot
import io.github.ayfri.kore.arguments.numbers.worldPos
import io.github.ayfri.kore.arguments.scores.score
import io.github.ayfri.kore.arguments.selector.scores
import io.github.ayfri.kore.arguments.types.literals.allPlayers
import io.github.ayfri.kore.arguments.types.literals.literal
import io.github.ayfri.kore.arguments.types.literals.rotation
import io.github.ayfri.kore.arguments.types.literals.self
import io.github.ayfri.kore.commands.attributes
import io.github.ayfri.kore.commands.bossBars
import io.github.ayfri.kore.commands.clear
import io.github.ayfri.kore.commands.effect
import io.github.ayfri.kore.commands.execute.execute
import io.github.ayfri.kore.commands.function
import io.github.ayfri.kore.commands.gamemode
import io.github.ayfri.kore.commands.scoreboard.scoreboard
import io.github.ayfri.kore.commands.spectate
import io.github.ayfri.kore.commands.tag
import io.github.ayfri.kore.commands.teams
import io.github.ayfri.kore.commands.tp
import io.github.ayfri.kore.commands.xp
import io.github.ayfri.kore.functions.function
import io.github.ayfri.kore.generated.Attributes
import io.github.ayfri.kore.generated.Effects
import java.util.UUID

/** Sets up player management. */
public object ManagePlayers : Configurable {

    /** The name of the reset player function. */
    public const val RESET_PLAYER_FUNCTION: String = "reset_player"

    /** The attribute used for removing gravity. */
    public val NO_GRAVITY_ATTRIBUTE: UUID = UUID.fromString("057b7e57-6d30-4c39-b65c-16efc5025383")

    override fun DataPack.configure() {
        tick("init_new_player") {
            execute {
                asTarget(
                    allPlayers {
                        tag = "!${CrossfireTags.INITIALIZED}"
                    }
                )

                run {
                    function(References.NAMESPACE, RESET_PLAYER_FUNCTION)
                }
            }
        }

        tick("tick_respawn") {
            // Tick down the dead timer
            execute {
                asTarget(
                    allPlayers {
                        tag = CrossfireTags.DIED

                        scores {
                            score(CrossfireScoreboards.DEAD_TIMER) greaterThanOrEqualTo 1
                        }
                    }
                )
                run {
                    scoreboard.players.remove(self(), CrossfireScoreboards.DEAD_TIMER, 1)
                }
            }

            // Tick down the respawn shield
            execute {
                asTarget(
                    allPlayers {
                        scores {
                            score(CrossfireScoreboards.RESPAWN_SHIELD) greaterThanOrEqualTo 1
                        }
                    }
                )
                run {
                    scoreboard.players.remove(self(), CrossfireScoreboards.RESPAWN_SHIELD, 1)
                }
            }
        }

        function(RESET_PLAYER_FUNCTION) {
            // Mark as initialized
            tag(self()) {
                // Give them the initialized tag if not already present
                add(CrossfireTags.INITIALIZED)

                // Remove spectator system tags
                for (playerIndex in 0 until References.PLAYER_COUNT) {
                    remove("${CrossfireTags.PLAYER}-$playerIndex")
                    remove("${CrossfireTags.SPECTATE_PLAYER}-$playerIndex")
                }
                remove(CrossfireTags.SPECTATING)
                remove(CrossfireTags.DIED_IN_WATER)
                remove(CrossfireTags.DIED)
                remove(CrossfireTags.RELOAD_CROSSBOW)

                remove(CrossfireTags.JOINED)
                remove(CrossfireTags.SELECTED)
                for (map in References.MAPS) {
                    remove("${CrossfireTags.JOINED}-$map")
                    remove("${CrossfireTags.SELECTED}-$map")
                }
                remove(CrossfireTags.SHOOTING_RANGE)

                // We always give people crossbows, a bit dangerous, but alright.
                add(CrossfireTags.GIVE_CROSSBOW)
            }

            // Remove them from any spectator target
            execute {
                asTarget(self())
                run {
                    spectate()
                }
            }

            // Reset game mode and state
            gamemode(Gamemode.ADVENTURE, self())
            effect(self()) {
                clear()
            }
            xp(self()) {
                set(Xp(0, ExperienceType.LEVELS))
                set(Xp(0, ExperienceType.POINTS))
            }

            // Remove the no gravity attribute
            attributes {
                get(self(), Attributes.GENERIC_GRAVITY) {
                    modifiers {
                        remove(NO_GRAVITY_ATTRIBUTE)
                    }
                }
            }

            // Add to the lobby team
            teams {
                join(CrossfireTeams.LOBBY_TEAM, self())
            }

            // Just clear the whole inventory
            clear(self())

            // Give QOL effects
            effect(self()) {
                clear()
                give(Effects.INSTANT_HEALTH, 1, 20, true)
                giveInfinite(Effects.JUMP_BOOST, 1, true)
                giveInfinite(Effects.SPEED, 0, true)
                giveInfinite(Effects.WEAKNESS, 255, true)
                giveInfinite(Effects.RESISTANCE, 255, true)
                giveInfinite(Effects.SATURATION, 255, true)
            }

            // Reset values
            scoreboard.players.set(self(), CrossfireScoreboards.INTRO, 0)
            scoreboard.players.set(self(), CrossfireScoreboards.IS_RELOADING, 0)
            scoreboard.players.set(self(), CrossfireScoreboards.RELOAD_TIMER, 0)
            scoreboard.players.set(self(), CrossfireScoreboards.DEAD_TIMER, 0)
            scoreboard.players.set(self(), CrossfireScoreboards.RESPAWN_SHIELD, 0)
            scoreboard.players.set(self(), CrossfireScoreboards.WINS, 0)
            scoreboard.players.set(self(), CrossfireScoreboards.ROUND_KILLS, 0)

            // Enable triggers
            scoreboard.players.enable(self(), CrossfireScoreboards.INTRO_START_TRIGGER)
            scoreboard.players.enable(self(), CrossfireScoreboards.INTRO_SKIPPED_TRIGGER)

            // Teleport player to their map (add 0 to map so it's at least 0)
            scoreboard.players.add(self(), CrossfireScoreboards.TARGET_MAP_INDEX, 0)
            execute {
                ifCondition { score(self(), CrossfireScoreboards.TARGET_MAP_INDEX) lessThanOrEqualTo 0 }
                run {
                    tp(self(), vec3(574.5.worldPos, 85.0.worldPos, 421.5.worldPos), rotation(90.0.rot, 0.0.rot))
                }
            }
            execute {
                ifCondition { score(self(), CrossfireScoreboards.TARGET_MAP_INDEX) greaterThanOrEqualTo 1 }
                run {
                    tp(self(), vec3(574.5.worldPos, 85.0.worldPos, 296.5.worldPos), rotation(90.0.rot, 0.0.rot))
                }
            }

            // Update the boss bar membership of each map
            for ((mapIndex, map) in References.MAPS.withIndex()) {
                clearBossBarPlayers(CrossfireBossbars.getTimer(map), References.NAMESPACE)
                clearBossBarPlayers(CrossfireBossbars.getPostGameTimer(map), References.NAMESPACE)

                execute {
                    ifCondition {
                        score(literal(map), CrossfireScoreboards.GAME_STATE) equalTo 2
                    }
                    run {
                        bossBars.get(CrossfireBossbars.getTimer(map), References.NAMESPACE).setPlayers(mapMembersSelector(mapIndex))
                    }
                }
                execute {
                    ifCondition {
                        score(literal(map), CrossfireScoreboards.GAME_STATE) equalTo 3
                    }
                    run {
                        bossBars.get(CrossfireBossbars.getPostGameTimer(map), References.NAMESPACE).setPlayers(mapMembersSelector(mapIndex))
                    }
                }
            }
        }
    }
}