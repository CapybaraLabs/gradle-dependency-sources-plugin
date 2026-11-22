// app gets core's dependencies transitively, but not core itself: its sources are already in the reactor
def appSources = new File(basedir, 'app/target/dependency-sources')
assert new File(appSources, 'org.apache.commons/commons-lang3/3.22.0/org/apache/commons/lang3/StringUtils.java').isFile()
assert !new File(appSources, 'it').exists()

def log = new File(basedir, 'build.log').text
assert !log.contains('no published sources')
