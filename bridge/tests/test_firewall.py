from hermes_remote_bridge import firewall


def _ufw(tmp_path, monkeypatch, rules: str, enabled: bool = True):
    conf, user = tmp_path / "ufw.conf", tmp_path / "user.rules"
    conf.write_text(f"ENABLED={'yes' if enabled else 'no'}\n")
    user.write_text(rules)
    real = firewall.Path
    monkeypatch.setattr(firewall, "Path", lambda p: {"/etc/ufw/ufw.conf": conf, "/etc/ufw/user.rules": user}.get(p, real(p)))
    return firewall._ufw_state(8650)


def test_ufw_blocked_until_every_private_range_is_allowed(tmp_path, monkeypatch):
    one = "-A ufw-user-input -p tcp --dport 8650 -s 192.168.0.0/16 -j ACCEPT\n"
    assert _ufw(tmp_path, monkeypatch, one).needs_opening
    full = "".join(f"-A ufw-user-input -p tcp --dport 8650 -s {r} -j ACCEPT\n" for r in firewall.PRIVATE_RANGES)
    assert not _ufw(tmp_path, monkeypatch, full).needs_opening


def test_ufw_port_open_to_everyone_counts_as_open(tmp_path, monkeypatch):
    assert _ufw(tmp_path, monkeypatch, "-A ufw-user-input -p tcp --dport 8650 -j ACCEPT\n").port_open


def test_inactive_ufw_needs_nothing(tmp_path, monkeypatch):
    assert not _ufw(tmp_path, monkeypatch, "", enabled=False).needs_opening


def test_open_commands_only_allow_private_ranges():
    for kind in ("ufw", "firewalld"):
        cmds = firewall.open_commands(kind, 8650)
        sources = [r for c in cmds for r in firewall.PRIVATE_RANGES if any(r in a for a in c)]
        assert sorted(sources) == sorted(firewall.PRIVATE_RANGES)
        assert not any("0.0.0.0/0" in a or a == "any" and c[c.index(a) - 1] == "from" for c in cmds for a in c)
    assert firewall.open_commands("unknown", 8650) == []
