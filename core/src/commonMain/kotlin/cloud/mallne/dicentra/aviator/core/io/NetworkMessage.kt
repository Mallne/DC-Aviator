package cloud.mallne.dicentra.aviator.core.io

interface NetworkMessage<H : NetworkHeader> {
    var headers: H
}