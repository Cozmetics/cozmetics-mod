# Portable ModBuilder Workflow

## What it is

This is a portable GitHub Actions workflow template that builds the Minecraft mod repository in which it is installed. It automatically detects the current repository, builds an exact selected revision with that repository's Gradle Wrapper, and publishes installable JARs to a generated release in the same repository.

## Why it exists

Use this template when a mod repository should build itself instead of being submitted to the central public-repository ModBuilder workflow. Because it runs inside the mod repository, it works with public and private repositories without copying private source into Mod-Builder.

## Install it

Copy:

```text
portable-mod-builder/.github/workflows/build-mod.yml
```

to your mod repository as:

```text
YOUR-MOD-REPOSITORY/.github/workflows/build-mod.yml
```

Then commit and push the copied workflow. The destination should resemble:

```text
My-Private-Mod/
├── .github/
│   └── workflows/
│       └── build-mod.yml
├── build.gradle
├── gradlew
└── src/
```

From the Mod-Builder working directory, an example copy command is:

```bash
mkdir -p /path/to/mod-repository/.github/workflows
cp portable-mod-builder/.github/workflows/build-mod.yml \
  /path/to/mod-repository/.github/workflows/build-mod.yml
```

## Run a build

1. Open the mod repository on GitHub.
2. Open **Actions**.
3. Choose **Build This Minecraft Mod**.
4. Use **Use workflow from** to choose the branch containing the workflow.
5. Leave **Optional tag or full commit SHA** empty to build the selected branch, or enter a tag or full commit SHA.
6. Select Java 17, 21, or 25.
7. Select `build`, `clean build`, `assemble`, or `remapJar`.
8. Click **Run workflow**.
9. Download the final JAR from the generated release.

### Build the selected branch

This is the normal mode. Select the branch using GitHub's **Use workflow from** selector and leave **Optional tag or full commit SHA** empty. The workflow builds the exact event commit from the selected branch, so the branch name does not need to be entered again.

The workflow file must exist on a branch for that branch to appear or work correctly through **Use workflow from**. Copy or merge the workflow onto every branch that users should be able to select.

### Build a tag

Enter the exact tag name in **Optional tag or full commit SHA**, for example:

```text
v1.2.0
```

The workflow validates the tag syntax, verifies that the tag exists, resolves annotated or lightweight tags to an exact commit, and builds that detached commit.

### Build a commit

Enter a full 40-character hexadecimal commit SHA, for example:

```text
5a639799f79744e4d723a10e3aef01030f312392
```

The workflow verifies that the commit is available in the repository history before building it.

### Automatic detection

ModBuilder automatically detects the optional field: an empty value means the selected workflow branch, exactly 40 hexadecimal characters means a commit SHA, and any other non-empty value is validated and resolved as a tag. The user never needs to specify the reference type.

## Native GitHub interface limitations

GitHub Actions workflow inputs are static. They cannot dynamically list repository tags or commits. GitHub's built-in **Use workflow from** selector is therefore used for branches, while tag names and commit SHAs share one optional text field.

## Private repository behavior

No personal access token is required. The built-in GitHub token checks out the repository and creates the release in that same repository. Source stays private, and only users who can access the private repository can view its workflow runs and releases.

The workflow intentionally uploads only final installable JARs. It does not create or attach source ZIP or TAR archives. GitHub may display its own automatic source links for the release tag; access to those links remains governed by the private repository's permissions.

Private submodules in another repository may still require additional credentials. This template does not request or forward those credentials.

## Repository requirements

The mod repository must:

- use Gradle and contain `gradlew`;
- build on an Ubuntu GitHub-hosted runner;
- support Java 17, 21, or 25; and
- produce installable JARs inside a `build/libs` directory.

The recursive JAR search supports ordinary single-module, multi-module, Fabric, Forge, NeoForge, Quilt, and multi-loader Gradle projects when their own build configuration meets these requirements.

Not every repository supports every predefined task. `build` is the recommended default, `clean build` clears old outputs first, `assemble` skips some verification work, and `remapJar` is available only in projects that define it.

## Permissions

The workflow requests only `contents: write`. This lets it check out the current repository and create a release and tag. It does not request personal access tokens, custom secrets, or other write permissions, and the token is not provided to Gradle.

If release creation is denied, open:

```text
Settings
→ Actions
→ General
→ Workflow permissions
→ Read and write permissions
```

## Common failures

- **Workflow missing from selected branch:** The workflow must exist on the branch chosen through **Use workflow from**.
- **Tag does not exist:** Check the exact tag spelling and confirm it exists in the repository.
- **Commit SHA is invalid:** Enter a full 40-character hexadecimal SHA.
- **Commit is unavailable:** The commit must exist in the fetched repository history.
- **Gradle Wrapper is missing:** Commit the repository's `gradlew` file.
- **Wrong Java version:** Select the version required by the mod project.
- **Gradle task is missing:** Not every project defines all four offered tasks.
- **No installable JAR was produced:** The project may use another task or a custom output directory.
- **Release write permission is disabled:** Enable read and write workflow permission in repository settings.
- **Private submodule is unavailable:** Cross-repository private submodules may require credentials this template does not accept.
- **Git LFS failed:** Required LFS objects may be unavailable or inaccessible.
- **Build timed out:** The workflow stops after 60 minutes.

If Gradle fails, available build reports and test results are retained for seven days. If compilation succeeds but release publishing fails, staged JARs are retained as a recovery artifact for seven days and the workflow is marked failed.

## Security notes

Build tasks are fixed choices mapped to fixed Gradle commands. User input is never evaluated as shell code. Tag names and commit SHAs are validated and quoted, checkout credentials are not persisted, and the GitHub token is available only to the dedicated release-publishing step.

Only build source you trust. Gradle scripts execute repository code on the GitHub-hosted runner.
