"""Trusted-circle roster helpers."""
from __future__ import annotations

from ..storage import get_db


def trusted_contacts(user_id: int) -> list[dict]:
    """Contacts of user_id that are marked trusted + linked."""
    db = get_db()
    out = []
    for c in db.list_contacts(user_id):
        if c.is_trusted or c.linked:
            out.append({"peer_id": c.peer_id, "label": c.label,
                        "relationship_tag": c.relationship_tag})
    return out


def is_linked(user_id: int, peer_id: int) -> bool:
    for c in get_db().list_contacts(user_id):
        if c.peer_id == peer_id and (c.linked or c.is_trusted):
            return True
    return False
