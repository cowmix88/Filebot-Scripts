// filebot -script fn:revert <file or folder>

def cleanup = false

// use args to list history only for the given folders if desired
def accept(from, to) {
	args.empty ? true : args.find{ to.absolutePath.startsWith(it.absolutePath) } && to.exists()
}

def revert(from, to) {
	def action = StandardRenameAction.forName(_args.action)
	
	println "[$action] Revert [$from] to [$to]"
	
	if (action == StandardRenameAction.MOVE && to.exists()) {

		println "[$action] To file exists, probably a link, removing... to.size()"

		if (Files.isSymbolicLink(to.toPath())) {
            println "[UNLINK] Remove Symlink [$to]"
            to.delete()
        } else if (to.size().equals(from.size())){
        	println "[UNLINK] Remove Hardlink [$to]"
			to.delete()
        }
	} 

	if (!from.canonicalFile.equals(to.canonicalFile)) {
		action.rename(from, to) // reverse-rename only if path has changed

	}

	tryQuietly{ to.xattr.clear() }
	cleanup = true
}

def clean() {
	println "[Cleanup] Running..."
	def doClean = tryQuietly{ cleanup }
	if (doClean){
		def cleanerInput = !args.empty ? args : []
		cleanerInput = cleanerInput.findAll{ f -> f.exists() }
		if (cleanerInput.size() > 0) {
			println 'Clean clutter files and empty folders'
			executeScript('cleaner', [root:true], cleanerInput)
		}
	}
}

getRenameLog(true).each { from, to ->
	if (accept(from, to))
		revert(to, from)
}
clean()
