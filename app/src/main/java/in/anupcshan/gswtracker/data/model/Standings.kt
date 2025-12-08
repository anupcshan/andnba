package `in`.anupcshan.gswtracker.data.model

/**
 * Conference standings data
 */
data class ConferenceStandings(
    val western: List<TeamStanding>,
    val eastern: List<TeamStanding>
)

data class TeamStanding(
    val teamId: Int,
    val teamTricode: String,
    val wins: Int,
    val losses: Int,
    val rank: Int,
    val gamesBack: Float, // GB from 1st place
    val conference: Conference
) {
    val winPct: Float get() = if (wins + losses > 0) wins.toFloat() / (wins + losses) else 0f
}

enum class Conference {
    WESTERN, EASTERN
}

/**
 * Conference membership - hardcoded as it rarely changes
 */
object ConferenceTeams {
    val western = setOf(
        1610612743, // DEN - Nuggets
        1610612744, // GSW - Warriors
        1610612745, // HOU - Rockets
        1610612746, // LAC - Clippers
        1610612747, // LAL - Lakers
        1610612763, // MEM - Grizzlies
        1610612750, // MIN - Timberwolves
        1610612740, // NOP - Pelicans
        1610612760, // OKC - Thunder
        1610612756, // PHX - Suns
        1610612757, // POR - Trail Blazers
        1610612758, // SAC - Kings
        1610612759, // SAS - Spurs
        1610612762, // UTA - Jazz
        1610612742  // DAL - Mavericks
    )

    val eastern = setOf(
        1610612738, // BOS - Celtics
        1610612751, // BKN - Nets
        1610612752, // NYK - Knicks
        1610612755, // PHI - 76ers
        1610612761, // TOR - Raptors
        1610612741, // CHI - Bulls
        1610612739, // CLE - Cavaliers
        1610612765, // DET - Pistons
        1610612754, // IND - Pacers
        1610612749, // MIL - Bucks
        1610612737, // ATL - Hawks
        1610612766, // CHA - Hornets
        1610612748, // MIA - Heat
        1610612753, // ORL - Magic
        1610612764  // WAS - Wizards
    )

    fun getConference(teamId: Int): Conference? {
        return when (teamId) {
            in western -> Conference.WESTERN
            in eastern -> Conference.EASTERN
            else -> null
        }
    }
}
