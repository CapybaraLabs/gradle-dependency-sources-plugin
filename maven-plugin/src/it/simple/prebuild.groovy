// leftovers of an earlier run, for a dependency that is no longer used
def stale = new File(basedir, 'target/dependency-sources/old.group/old/1.0/Stale.java')
stale.parentFile.mkdirs()
stale.text = 'class Stale {}'
true
