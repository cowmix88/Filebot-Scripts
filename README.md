# Filebot-Scripts
My custom scripts for filebot

Additonal features to amc.groovy:
* Notify Sonarr to update series disk scan
  * (Use this with disabled rename and completed download move in sonarr) 
  * example usage: "sonarr=url:port/basepath|token"
* Notify Couchpotato to update series disk scan (Use this with disabled rename in couchpotato)
  * example usage: "potato=url:port/basepath|token"
* Updated Plex to only update libraries that have changed instead of entire library,
  * example usage: "plex=url:port/basepath|token"
* Search video files for embedded subtitles and skip opensubtitle search if found
* Search AniDB but use TVDB for seasons and episode listing 
  * (uses the amazing list mapping from https://github.com/ScudLee/anime-lists )
  * (primayTitle is updated with AniDB title, n is TVDB)
* Search AniDB but use TMDB for movie renaming 
  * (uses the amazing list mapping from https://github.com/ScudLee/anime-lists ) 
  * (primayTitle is updated with AniDB title, n is TMDB)
* Some hopefully good updates to anime detection, may be buggy since I primarily use label detection
* Do not notify Sonarr, Plex, CouchPotato or Pushbullet if running in test mode
* Switch to dvd order in TVDB if bluray/dvd/bdrip/dvdrip tags found
* Custom handling of acoustid responses to try to select the correct album and handling failed matches
