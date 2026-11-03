"""ticketing-simulator 아키텍처 그림(architecture.png) 생성 스크립트.

실행: pip install diagrams (Graphviz 필요) 후 docs 폴더에서 `python architecture.py`
"""
from diagrams import Cluster, Diagram, Edge
from diagrams.onprem.client import Client
from diagrams.onprem.compute import Server
from diagrams.onprem.database import MySQL
from diagrams.onprem.inmemory import Redis
from diagrams.onprem.queue import Kafka
from diagrams.programming.framework import React, Spring

GRAPH = {"fontsize": "22", "pad": "0.6", "nodesep": "0.55", "ranksep": "1.3", "splines": "spline"}
CLUSTER = {"bgcolor": "#E5F5FD", "pencolor": "#AEB6BE", "fontsize": "13", "style": "rounded"}


# 선은 모두 왼쪽(그림 앞쪽) 노드에서 오른쪽 노드로 선언해 배치를 고정한다.
# 실제 흐름이 오른쪽에서 왼쪽이면 `a << edge << b`로 써서 화살표만 a를 향하게 한다.
def sync(label=""):
    """HTTP/WebSocket 호출과 저장소 접근."""
    return Edge(color="#7B8894", label=label, fontsize="11", fontcolor="#5A6570")


def event(label=""):
    """Kafka를 거치는 비동기 이벤트."""
    return Edge(color="#E07B39", style="bold", label=label, fontsize="11", fontcolor="#B85F25")


with Diagram("Ticketing Simulator", filename="architecture", outformat="png", show=False,
             direction="LR", graph_attr=GRAPH):
    with Cluster("Clients", graph_attr=CLUSTER):
        web = React("web")
        k6 = Client("k6 audience")

    # API와 Consumer/스케줄러는 같은 Spring Boot 앱(모놀리식)이다. Kafka는 앱 밖의 브로커라 두 칸 사이에 둔다.
    with Cluster("ticketing-api: web", graph_attr=CLUSTER):
        api = Spring("REST API")
        ws = Spring("WebSocket\n(STOMP)")

    with Cluster("Kafka", graph_attr=CLUSTER):
        dead = Kafka("*.DLT")
        requested = Kafka("entry.requested")
        granted = Kafka("entry.granted")
        seat = Kafka("seat.confirmed\nseat.released")

    with Cluster("ticketing-api: consumers / schedulers", graph_attr=CLUSTER):
        dlt = Server("DLT consumers")
        worker = Server("queue worker")
        entry_relay = Server("entry relay")
        stats = Server("stats consumer")
        outbox = Server("outbox relay")
        ttl = Server("hold TTL\nscheduler")
        cleanup = Server("run cleanup\nscheduler")

    with Cluster("Storage", graph_attr=CLUSTER):
        redis = Redis("queue, token\nlock, stats")
        mysql = MySQL("concerts, events, seats\npayments, outbox")

    web >> sync("REST") >> api
    k6 >> sync("REST") >> api
    web << sync("push") << ws

    api >> sync("seat status") >> ws
    api >> event() >> requested
    api >> sync("queue, token\nlock, stats") >> redis
    api >> sync("hold, pay") >> mysql

    requested >> event() >> worker
    granted << event() << worker
    redis << sync("token, dequeue") << worker
    granted >> event() >> entry_relay
    ws << sync("granted") << entry_relay

    granted >> event() >> stats
    seat >> event() >> stats
    redis << sync() << stats

    seat << event() << outbox
    mysql << sync("poll") << outbox
    mysql << sync("release + outbox") << ttl
    ws << sync("seat status") << ttl
    mysql << sync("delete old runs") << cleanup

    dead >> event() >> dlt
    requested << event("redrive") << dlt
    redis << sync("failed, stats") << dlt
