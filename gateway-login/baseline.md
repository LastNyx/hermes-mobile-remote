# Baseline: gateway-login (hermes-mobile-remote)

Before the first ticket, at d57cab0. Failures below already existed on main; they are not this feature's findings. A NEW failure is.

### bridge: `uv run pytest -q` in `bridge` exited 1

```text
        db.commit(); db.close()
        c, token = _profile_app(tmp_path, [], hermes_home=home)
        url = "/v1/projects/p_1/idea"
    
        r = c.get(url, headers=auth(token)).json()
        assert r == {"path": str(folder / "IDEA.md"), "exists": False, "text": ""}
        assert c.put(url, json={"text": "# My app\nA shop app."}, headers=auth(token)).status_code == 200
        assert (folder / "IDEA.md").read_text() == "# My app\nA shop app.\n"
>       assert c.get(url, headers=auth(token)).json()["text"] == "# My app\nA shop app.\n"
E       AssertionError: assert '# My app\r\nA shop app.\r\n' == '# My app\nA shop app.\n'
E         
E         - # My app
E         + # My app
E         ?         +
E         - A shop app.
E         + A shop app.
E         ?            +

tests\test_app.py:720: AssertionError
=========================== short test summary info ===========================
FAILED tests/test_app.py::test_media_serves_only_images_in_hermes_image_folders - OSError: [WinError 1314] A required privilege is not held by the client: 'C:\\Users\\User\\AppData\\Local\\Temp\\pytest-of-User\\pytest-38\\test_media_serves_only_images_0\\hermes\\.env.png' -> 'C:\\Users\\User\\AppData\\Local\\Temp\\pytest-of-User\\pytest-38\\test_media_serves_only_images_0\\hermes\\images\\link.png'
FAILED tests/test_app.py::test_project_idea_reads_and_writes_only_the_projects_own_idea_md - AssertionError: assert '# My app\r\nA shop app.\r\n' == '# My app\nA shop app.\n'
  
  - # My app
  + # My app
  ?         +
  - A shop app.
  + A shop app.
  ?            +
2 failed, 125 passed in 34.45s
```

### android: `bash build.sh test` in `android` exited 0

```text

```

## Failing tests (read by kit/suites.py)

- bridge: tests/test_app.py::test_media_serves_only_images_in_hermes_image_folders
- bridge: tests/test_app.py::test_project_idea_reads_and_writes_only_the_projects_own_idea_md
