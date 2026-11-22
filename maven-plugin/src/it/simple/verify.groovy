def sources = new File(basedir, 'target/dependency-sources')

assert new File(sources, 'org.apache.commons/commons-lang3/3.22.0/org/apache/commons/lang3/StringUtils.java').text.contains('public class StringUtils')
assert new File(sources, 'com.google.guava/guava/33.8.0-jre/com/google/common/collect/ImmutableList.java').isFile()
assert new File(sources, 'com.google.guava/failureaccess').isDirectory() // transitive
assert !new File(sources, 'old.group').exists() // stale sources from an earlier run are removed

def log = new File(basedir, 'build.log').text
assert log.contains('Unpacked sources of')
assert log.contains('dependencies have no published sources') // guava's listenablefuture placeholder
