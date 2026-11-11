public enum TipoPaquete {
    SOLICITUD_CATALOGO((byte) 0x01),
    RESPUESTA_CATALOGO((byte) 0x02),
    SOLICITUD_ARCHIVO((byte) 0x03),
    DATOS((byte) 0x04),
    CONFIRMACION((byte) 0x05),
    FIN((byte) 0x06),
    ERROR((byte) 0x07);

    private final byte codigo;

    TipoPaquete(byte codigo) {
        this.codigo = codigo;
    }

    public byte obtenerCodigo() {
        return codigo;
    }

    public static TipoPaquete desdeCodigo(byte codigo) {
        for (TipoPaquete tipo : values()) {
            if (tipo.codigo == codigo) {
                return tipo;
            }
        }
        return null;
    }
}