## DeadChest - WorldGuard Integration

DeadChest can use WorldGuard regions to control where a DeadChest may be generated.

### Prerequisites

- DeadChest installed and running
- WorldGuard installed
- Integration enabled in `config.yml`

### Enable integration

Set the following keys in `config.yml`:

```yaml
integrations:
  worldguard:
    enabled: true
    default-allow: false
```

- `enabled`: activates WorldGuard checks.
- `default-allow`: fallback when no explicit DeadChest flag result applies.

### DeadChest WorldGuard flags

DeadChest registers these custom region flags:

| Flag        | Meaning                                              |
|-------------|------------------------------------------------------|
| `dc-owner`  | Region owners can generate DeadChest in the region.  |
| `dc-member` | Region members can generate DeadChest in the region. |
| `dc-guest`  | Players who are neither owners nor members.          |

### How flags are evaluated

At the death location, each region uses the flag matching the player's role:
`dc-owner` for owners, `dc-member` for other members, and `dc-guest` for everyone
else. An owner uses only `dc-owner`, even if also listed as a member.
Membership includes WorldGuard permission groups and inherited membership.

WorldGuard resolves parent flags and region-group restrictions. Among applicable
decisions, higher-priority regions take precedence. At equal priority, a child
can override its parent; conflicting unrelated regions resolve to `deny`.
The global region supplies a fallback below local regions.

If no applicable flag is defined, DeadChest uses
`integrations.worldguard.default-allow`. Flags for other roles do not affect
the player.

### Example commands (WorldGuard)

Command syntax may vary slightly by WorldGuard version, but typically:

```text
/rg flag <region> dc-owner allow
/rg flag <region> dc-member allow
/rg flag <region> dc-guest deny
```

### Verify integration

- Restart/reload server.
- Check console logs for WorldGuard detection and flag behavior.
- Test player deaths in and out of flagged regions.

### Troubleshooting

- If no custom flags appear, ensure WorldGuard is loaded before DeadChest check happens.
- If behavior seems global, verify the exact region at death location.
- If unsure, temporarily set `default-allow: true` to compare behavior.

