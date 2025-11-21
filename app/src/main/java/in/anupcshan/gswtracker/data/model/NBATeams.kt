package `in`.anupcshan.gswtracker.data.model

data class NBATeam(
    val tricode: String,
    val name: String,
    val city: String
) {
    val fullName: String get() = "$city $name"
}

object NBATeams {
    val ALL_TEAMS = listOf(
        NBATeam("ATL", "Hawks", "Atlanta"),
        NBATeam("BOS", "Celtics", "Boston"),
        NBATeam("BKN", "Nets", "Brooklyn"),
        NBATeam("CHA", "Hornets", "Charlotte"),
        NBATeam("CHI", "Bulls", "Chicago"),
        NBATeam("CLE", "Cavaliers", "Cleveland"),
        NBATeam("DAL", "Mavericks", "Dallas"),
        NBATeam("DEN", "Nuggets", "Denver"),
        NBATeam("DET", "Pistons", "Detroit"),
        NBATeam("GSW", "Warriors", "Golden State"),
        NBATeam("HOU", "Rockets", "Houston"),
        NBATeam("IND", "Pacers", "Indiana"),
        NBATeam("LAC", "Clippers", "LA"),
        NBATeam("LAL", "Lakers", "Los Angeles"),
        NBATeam("MEM", "Grizzlies", "Memphis"),
        NBATeam("MIA", "Heat", "Miami"),
        NBATeam("MIL", "Bucks", "Milwaukee"),
        NBATeam("MIN", "Timberwolves", "Minnesota"),
        NBATeam("NOP", "Pelicans", "New Orleans"),
        NBATeam("NYK", "Knicks", "New York"),
        NBATeam("OKC", "Thunder", "Oklahoma City"),
        NBATeam("ORL", "Magic", "Orlando"),
        NBATeam("PHI", "76ers", "Philadelphia"),
        NBATeam("PHX", "Suns", "Phoenix"),
        NBATeam("POR", "Trail Blazers", "Portland"),
        NBATeam("SAC", "Kings", "Sacramento"),
        NBATeam("SAS", "Spurs", "San Antonio"),
        NBATeam("TOR", "Raptors", "Toronto"),
        NBATeam("UTA", "Jazz", "Utah"),
        NBATeam("WAS", "Wizards", "Washington")
    )

    fun getTeamByTricode(tricode: String): NBATeam? {
        return ALL_TEAMS.find { it.tricode == tricode }
    }

    val DEFAULT_TEAM = ALL_TEAMS.find { it.tricode == "GSW" }!!
}
