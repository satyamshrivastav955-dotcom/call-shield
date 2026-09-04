"""LangGraph StateGraph built from the orchestration nodes (MD 4.5 topology)."""
from __future__ import annotations

import logging

from langgraph.graph import END, StateGraph

from .nodes import (collective_check_node, decision_node, fusion_node,
                    identity_claim_node, intent_node, llm_reasoning_node,
                    router_node, text_detector_node, urgency_node,
                    video_detector_node, voice_detector_node)
from .state import AnalysisState

log = logging.getLogger(__name__)


def _route_after_router(state: AnalysisState) -> str:
    # voice/video events -> voice/video detectors; messages -> text detectors
    return "text" if state.get("kind") == "message" else "media"


def _route_after_fusion(state: AnalysisState) -> str:
    # both branches converge back to the LLM reasoning node
    return "llm"


def build_graph():
    g = StateGraph(AnalysisState)

    # nodes
    g.add_node("router", router_node)
    g.add_node("media_detectors", _media_detectors)
    g.add_node("text_detectors", text_detector_node)
    g.add_node("identity", identity_claim_node)
    g.add_node("collective", collective_check_node)
    g.add_node("urgency", urgency_node)
    g.add_node("intent", intent_node)
    g.add_node("fusion", fusion_node)
    g.add_node("decision", decision_node)
    g.add_node("llm", llm_reasoning_node)

    # edges
    g.set_entry_point("router")
    g.add_conditional_edges("router", _route_after_router,
                            {"text": "text_detectors", "media": "media_detectors"})
    g.add_edge("media_detectors", "identity")
    g.add_edge("text_detectors", "collective")
    g.add_edge("identity", "collective")
    g.add_edge("collective", "urgency")
    g.add_edge("urgency", "intent")
    g.add_edge("intent", "fusion")
    g.add_edge("fusion", "decision")
    g.add_edge("decision", "llm")
    g.add_edge("llm", END)

    return g.compile()


async def _media_detectors(state: AnalysisState) -> AnalysisState:
    await voice_detector_node(state)
    await video_detector_node(state)
    # A call is also a text stream: run the scam-pattern + behavioral detectors
    # on the running transcript so scam_prob/scam_type/deviation are live during
    # a call too (the graph routes calls here, NOT to text_detectors, so without
    # this the scam signal would always read 0 on voice/video calls).
    if state.get("text"):
        await text_detector_node(state)
    return state


_graph = None


def get_graph():
    global _graph
    if _graph is None:
        try:
            _graph = build_graph()
        except Exception as e:
            log.warning("LangGraph build failed (%s) - using sequential fallback", e)
            _graph = None
    return _graph
