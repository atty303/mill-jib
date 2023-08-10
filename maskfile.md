## publish-local

```bash
./mill __.publishLocal
```

## publish
> Publish artifacts to sonatype

```bash
./mill mill.scalalib.PublishModule/publishAll \
  __.publishArtifacts \
  --sonatypeUri https://s01.oss.sonatype.org/service/local \
  --sonatypeCreds $SONATYPE_USER:$SONATYPE_PASSWORD \
  --release true
```
