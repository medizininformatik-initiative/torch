# Custom Consent Modules

The MII Broad Consent pipeline documented in [Consent Handling](consent.md) is TORCH's **default** consent
implementation, not a fixed part of the core application. Consent handling sits behind a `ConsentEvaluator`
extension point ([#1068](https://github.com/medizininformatik-initiative/torch/issues/1068)), so institutions
with a different consent model can supply their own implementation — including one that only partially
resembles the gate/window/retro-modifier model — **without forking or rebuilding TORCH**.

## How it's packaged

TORCH's build is split into three Maven modules:

| Module | Contains |
|---|---|
| `torch-consent-api` | The `ConsentEvaluator` interface and its supporting types (`ConsentContext`, `PatientSet`, `ConsentDataClient`). Stable extension surface. |
| `torch-consent-mii` | The MII Broad Consent implementation documented in [Consent Handling](consent.md). Built as its own jar, not compiled into the main application jar. |
| `torch-app` | The core application. Depends on `torch-consent-mii` only for its own tests — the production image never bundles it directly. |

The main application jar is launched with Spring Boot's `PropertiesLauncher`, which adds every jar found in a
plugins directory (`LOADER_PATH`, default `/app/plugins` — see [Configuration](../configuration.md)) to the classpath
at startup, on top of the jars already bundled inside it. The Docker image ships the MII jar in that directory as
a separate file specifically so it can be swapped out.

## Replacing the default implementation

1. Implement `ConsentEvaluator` from `torch-consent-api` (in any package — it does not need to live under
   `de.medizininformatikinitiative.torch`):
   - `validate(ConsentContext)` — runs once when a CRTDL is submitted; reject malformed consent criteria by throwing.
   - `evaluate(ConsentContext, PatientSet)` — runs once per patient batch; returns the consent-permitted period per
     patient, or `Optional.empty()` if no consent requirement applies to this request.
2. Register the implementation as a Spring bean via a `@AutoConfiguration` class listed in your own
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (this is how it's discovered —
   plain `@Component` scanning does not reach classes outside TORCH's own packages). Guard the bean with
   `@ConditionalOnMissingBean(ConsentEvaluator.class)` if you want to keep the MII implementation as a fallback when
   your jar is absent.
3. Build the jar and place it in the plugins directory in place of (or alongside, if using
   `@ConditionalOnMissingBean`) `torch-consent-mii.jar`, then restart the container. No TORCH rebuild is required.

If the plugins directory ends up empty, TORCH fails to start with `NoSuchBeanDefinitionException` for
`ConsentEvaluator` — startup without any consent evaluation is intentionally not supported.

## Mounting the plugins directory

Getting a replacement jar into `/app/plugins` without rebuilding the image is a Docker volume-mounting
question, not a TORCH one, but it has two sharp edges worth calling out:

```yaml
services:
  torch:
    image: ghcr.io/medizininformatik-initiative/torch:latest
    volumes:
      - torch-data-store:/app/output
      - ./plugins:/app/plugins    # replaces the image's plugins directory entirely
```

- **Bind mounts shadow, they don't merge.** A bind mount like `./plugins` above replaces the image's
  `/app/plugins` completely — the `torch-consent-mii.jar` baked into the image is no longer visible to the
  container. `./plugins` on the host must therefore contain every jar you want loaded: your replacement
  `ConsentEvaluator` jar, plus `torch-consent-mii.jar` itself if you're using `@ConditionalOnMissingBean`
  and want it available as a fallback. A named volume behaves differently the first time it's used — Docker
  pre-populates it from the image's `/app/plugins`, so the MII jar stays present automatically — but a
  reused volume behaves like a bind mount, with none of that pre-population.
- **Permissions travel with the mount, not the image.** Files must be readable by UID `1001`, the non-root
  user TORCH runs as — the image's own `chown` to that user only applies to files baked into the image, not
  to anything mounted in from outside. An unreadable plugins directory looks the same as an empty one:
  `NoSuchBeanDefinitionException` for `ConsentEvaluator` at startup.
