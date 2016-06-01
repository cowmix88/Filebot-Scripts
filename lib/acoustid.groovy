import static net.filebot.web.WebRequest.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.swing.Icon;

import net.filebot.Cache;
import net.filebot.ResourceManager;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;

import net.filebot.web.*;
import static net.filebot.WebServices.*;

import net.filebot.mediainfo.MediaInfo;
import net.filebot.mediainfo.MediaInfo.StreamKind;

public class CustomAcoustID {

	private static void matchField(matches, AudioTrack track, String matchfield, String trackfield){
		def match = matches[matchfield].find { 
			if (track[trackfield].equals(it.val)) { 
				it.count++; return it 
			} 
		} 
		if (!match) {
			matches[matchfield].add([val:track[trackfield], count:1])
		}
	}

	private static void setTrackMatches(matches, AudioTrack track) {

		matchField(matches, track, 'id', 'MBID')
		matchField(matches, track, 'album', 'album')
		matchField(matches, track, 'albumArtist', 'albumArtist')
		matchField(matches, track, 'date', 'albumReleaseDate')
		matchField(matches, track, 'trackCount', 'trackCount')
		matchField(matches, track, 'medium', 'medium')
		matchField(matches, track, 'mediumCount', 'mediumCount')

		/*if (!matches.id.find{ if (track.getMBID().equals(it.val)) { it.count++; return it } } )
			matches.id.add([val:track.getMBID(), count:1])

		if (!matches.album.find{ if (track.getAlbum().equals(it.val)) { it.count++; return it } } )
			matches.album.add([val:track.getAlbum(), count:1])

		if (!matches.albumArtist.find{ if (track.getAlbumArtist().equals(it.val)) { it.count++; return it } } )
			matches.albumArtist.add([val:track.getAlbumArtist(), count:1])
			
		if (!matches.date.find{ if (track.getAlbumReleaseDate().equals(it.val)) { it.count++; return it } } )
			matches.date.add([val:track.getAlbumReleaseDate(), count:1])
			
		if (!matches.trackCount.find{ if (track.getTrackCount().equals(it.val)) { it.count++; return it } } )
			matches.trackCount.add([val:track.getTrackCount(), count:1])
		
		if (!matches.medium.find{ if (track.getMedium().equals(it.val)) { it.count++; return it } } )
			matches.medium.add([val:track.getMedium(), count:1])

		if (!matches.mediumCount.find{ if (track.getMediumCount().equals(it.val)) { it.count++; return it } } )
			matches.mediumCount.add([val:track.getMediumCount(), count:1])*/
	}

	public Map<File, AudioTrack> lookup(Collection<File> files, filters) throws Exception {
		Map<File, AudioTrack> results = new LinkedHashMap<File, AudioTrack>();

		def matches = [album: [], albumArtist: [], date: [], trackCount: [], medium:[], mediumCount:[], id:[]]
		def skipped = []

		if (files.size() > 0) {
			for (Map<String, String> fp : fpcalc(files)) {
				File file = new File(fp.get("FILE"));
				int duration = Integer.parseInt(fp.get("DURATION"));
				String fingerprint = fp.get("FINGERPRINT");

				if (duration > 1 && fingerprint != null) {
					String response = lookup(duration, fingerprint);
					if (response != null && response.length() > 0) {
						AudioTrack track = parseResult(response, duration, filters)
						if (track){
							println 'Matched: ' + file.getName()
							println ('Custom: Album Artist: ' + track.getAlbumArtist() + ', Album: ' + track.getAlbum() + ", CD: " + track.getMedium() + "/" + track.getMediumCount() + ", Tracks: " + track.getTrack().toString().pad(2)  + "/" + track.getTrackCount().toString().pad(2) + ', Artist: ' + track.getArtist() + ", Title: " + track.getTitle())
							setTrackMatches(matches, track)
							results.put(file, track);
						} else {
							println 'Skipped: ' + file.getName()
							skipped.add(file)
						}
					}
				}
			}
		}

		println ("Matches: " + matches.toString())
		println ("Skipped: " + skipped.size())

		if (skipped.size() >= files.size()){
			println("[$filters.group]: Could not match to any tracks with acoustid")
		} else {
			println ("Trying to match failed acoustid tracks relatives to matched tracks")

		}

		def match = [album: matches.album.sort{ 1 / it.count }.findResult{ return it.val }, 
						albumArtist: matches.albumArtist.sort{ 1 / it.count }.findResult{ return it.val }, 
						date: matches.date.sort{ 1 / it.count }.findResult{ return it.val }, 
						trackCount: matches.trackCount.sort{ 1 / it.count }.findResult{ return it.val },
						medium: matches.medium.sort{ 1 / it.count }.findResult{ return it.val },
						mediumCount: matches.mediumCount.sort{ 1 / it.count }.findResult{ return it.val },
						id: matches.id.sort{ 1 / it.count }.findResult{ return it.val }
				]

		println ("Match: " + matches.toString())

		skipped.each{ file ->

			def title = ''
			MediaInfo mediaInfo = new MediaInfo();
			try {
				mediaInfo.open(file)

				title = mediaInfo.get(StreamKind.General, 0, "Title");

				if (title.isEmpty())
					title = mediaInfo.get(StreamKind.General, 0, "Track");

			} finally {
				mediaInfo.close();
				if (title.isEmpty())
					title = it.getName().replaceFirst(/[.][^.]+$/, "")
			}

			println "Matching Skipped File: [$file.name] => [$title]"

			def query = java.net.URLEncoder.encode(title.replaceAll(/[\[\]!?.]+/, ''))

			println "XML Resource: [$file.name] => [$title]"

			dom = Cache.getCache('anime_xml_store', CacheType.Persistent).xml(query) { 
				   new URL('http://musicbrainz.org/ws/2/recording?query='+it+'&limit=1')
			}.expire(Cache.ONE_MNOTH).get()

			def recording = net.filebot.util.XPathUtilities.selectNode("metadata/recording-list/recording", dom)
			title = unescape(net.filebot.util.XPathUtilities.selectString("title", recording))

			def artist = ''
			net.filebot.util.XPathUtilities.selectNodes("artist-credit/name-credit", recording).each{ artists ->
				def join = net.filebot.util.XPathUtilities.evaluateXPath("@joinphrase", artists, javax.xml.xpath.XPathConstants.STRING) ?: ''
				artist += unescape(net.filebot.util.XPathUtilities.selectString("artist/name", artists)) + unescape(join)
			}

			if (match.id){
				println ("$match.id")
				try {
					def release = net.filebot.util.XPathUtilities.selectNode("release-list/release[@id='$match.id']", recording)

					def med = net.filebot.util.XPathUtilities.selectNode("medium-list/medium", release)
					def medium = net.filebot.util.XPathUtilities.selectString("position", med).toInteger()
					def tracki = net.filebot.util.XPathUtilities.selectString("track-list/track/number", med).toInteger()

					def track = new AudioTrack(artist, title, match.album, match.albumArtist, null, match.date, medium, match.mediumCount, tracki, match.trackCount, null)
					results.put(file, track)
				} catch  (Exception e) {
					println("[$filters.group]: match release threw error")
				}

			} else {
				println("[$filters.group]: match release id is null")
			}
		}

		return results;
	}

	public AudioTrack parseResult(String json, final int targetDuration, filters = null) throws IOException {

		//println 'JSON:' + json

		def jsonObj = (new groovy.json.JsonSlurper()).parseText(json)

		//println json
		if ("ok" != jsonObj.status) {
			throw new IOException("acoustid responded with error: " + data.get("status"));
		}

		//println 'JSON Obj:' + jsonObj
		def allTracks = []
		jsonObj.results.each{ result ->
			//println 'Result:' + result
			def audioTracks = []
			result.recordings.each{ recording ->	
				//println 'Recording:' + recording
				def duration = recording.duration
				def artist = ''
				recording.artists.each{ 
					artist += it.name + (it.joinphrase ?: '')
				}
				def title = recording.title;

				recording.releasegroups.each{ releasegroup ->
					def type = releasegroup.type
					def album = releasegroup.title
					def albumArtist = ''
					releasegroup.artists.each{ 
						albumArtist += it.name + (it.joinphrase ?: '')
					}
					if (albumArtist.isEmpty())
						albumArtist = artist

					//def albumArtistId = releasegroup.artists[0].id

					releasegroup.releases.each{ release ->
						def month = null
						def day = null
						def year = null

						if (release.date) {
							month = release.date.month
							day = release.date.day
							year = release.date.year
						}

						def mediumCount = release.medium_count
						def country = release.country

						def releaseId = release.id

						release.mediums.each{ med ->
							def medium = med.position
							def trackCount = med.track_count
							med.tracks.each{ tra ->
								def track = tra.position
								//def trackid = tra.id
								def id = releaseId//trackid
								//def id = "Arist:" + albumArtistId + "|Track:" + trackid
								allTracks.add([duration:duration, artist:artist, title:title, type:type, album:album, albumArtist:albumArtist, month:month, day:day, year:year, mediumCount:mediumCount, country:country, medium:medium, trackCount:trackCount, track:track, id:id])
							}
														
						}
					}
				}
			}
		}

		//println 'Found ' + allTracks.size() + ' possible matches'

		allTracks.sort{ o1, o2 ->
			def i1 = o1.duration
			def i2 = o2.duration
			return Double.compare(i1 == null ? Double.NaN : Math.abs(i1 - targetDuration), i2 == null ? Double.NaN : Math.abs(i2 - targetDuration));
		}

		allTracks = allTracks.sort{
			allTracks.year
		}

		allTracks = allTracks.sort{
			return allTracks.country == "US" ? 1 : 2
		}

		if (filters){
			if (filters.tracks){
				allTracks = allTracks.grep{
					(it.medium * it.trackCount) >= filters.tracks
				}.sort{
					it.medium * it.trackCount
				}
			}

			if (filters.group){
				allTracks = allTracks.sort{
					2 - (filters.group.getSimilarity(it.album) + filters.group.getSimilarity(it.year.toString()))
				}
			}

		}

		//println 'Found ' + allTracks.size() + ' possible matches after filtering'

		//allTracks.each{
		//	println it
		//}

		allTracks.sort{ o1, o2 ->
			def i1 = o1.duration
			def i2 = o2.duration
			return Double.compare(i1 == null ? Double.NaN : Math.abs(i1 - targetDuration), i2 == null ? Double.NaN : Math.abs(i2 - targetDuration));
		}

		def audioTrack = allTracks[0]
		if (audioTrack) {
			//println 'found'
			return new AudioTrack(audioTrack.artist, audioTrack.title, audioTrack.album, audioTrack.albumArtist, null, new SimpleDate(audioTrack.year ?: 0, audioTrack.month ?: 0, audioTrack.day ?: 0), audioTrack.medium, audioTrack.mediumCount, audioTrack.track, audioTrack.trackCount, audioTrack.id, null)
		}
		return null
	}

	public static String lookup(int duration, String fingerprint) throws IOException, InterruptedException {
		return AcoustID.lookup(duration, fingerprint);
	}

	public static String unescape(String s) {
		return s ? s.replaceAll('&amp;', '&') : ''
	}

	public String getChromaprintCommand() {
		return AcoustID.getChromaprintCommand();
	}

	public List<Map<String, String>> fpcalc(Collection<File> files) throws IOException, InterruptedException {
		return AcoustID.fpcalc(files);
	}
}
