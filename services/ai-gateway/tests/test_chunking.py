import pytest

from app.chunking import chunk_text


def test_empty_text_gives_no_chunks():
    assert chunk_text("   \n ", 10, 2) == []


def test_short_text_is_one_chunk():
    assert chunk_text("a b c", 10, 2) == ["a b c"]


def test_windows_overlap_and_cover_everything():
    words = [f"w{i}" for i in range(25)]
    chunks = chunk_text(" ".join(words), 10, 3)
    assert chunks[0].split() == words[0:10]
    assert chunks[1].split() == words[7:17]  # starts 3 words before chunk 0 ended
    assert chunks[-1].split()[-1] == "w24"
    assert len(chunks) == 4  # starts at 0, 7, 14, 21


def test_exact_multiple_has_no_redundant_tail_chunk():
    chunks = chunk_text(" ".join(f"w{i}" for i in range(10)), 10, 2)
    assert len(chunks) == 1


@pytest.mark.parametrize("size,overlap", [(0, 0), (5, 5), (5, -1)])
def test_invalid_params_rejected(size, overlap):
    with pytest.raises(ValueError):
        chunk_text("a b c", size, overlap)
