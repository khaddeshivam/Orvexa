import httpx
import pytest

from app.llm import LLMUnavailableError, OllamaProvider


class _Resp:
    def __init__(self, payload, status=200):
        self._payload, self.status_code = payload, status

    def raise_for_status(self):
        if self.status_code >= 400:
            raise httpx.HTTPStatusError("err", request=None, response=None)

    def json(self):
        return self._payload


def test_ollama_request_shape_and_parse(monkeypatch):
    seen = {}

    def fake_post(url, json, timeout):
        seen.update(url=url, json=json, timeout=timeout)
        return _Resp({"message": {"role": "assistant", "content": "  hello [1] \n"}})

    monkeypatch.setattr(httpx, "post", fake_post)
    out = OllamaProvider("http://ollama:11434/", "m", timeout=5).generate("sys", "prompt")

    assert out == "hello [1]"
    assert seen["url"] == "http://ollama:11434/api/chat"
    assert seen["json"]["stream"] is False
    assert seen["json"]["messages"][0] == {"role": "system", "content": "sys"}


@pytest.mark.parametrize("failure", ["connect", "status", "bad_body"])
def test_ollama_failures_become_llm_unavailable(monkeypatch, failure):
    def fake_post(url, json, timeout):
        if failure == "connect":
            raise httpx.ConnectError("refused")
        if failure == "status":
            return _Resp({}, status=500)
        return _Resp({"unexpected": True})

    monkeypatch.setattr(httpx, "post", fake_post)
    with pytest.raises(LLMUnavailableError):
        OllamaProvider("http://x", "m").generate("s", "p")
