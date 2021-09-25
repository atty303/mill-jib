## publish-local

```bash
mill __.publishLocal
```

## publish
> Publish artifacts to sonatype

```bash
mill mill.scalalib.PublishModule/publishAll \
  $SONATYPE_USER:$SONATYPE_PASSWORD \
  $GPG_PASSWORD \
  __.publishArtifacts \
  --release true
```
