<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="html" encoding="UTF-8"/>
    <xsl:template match="/*">
        <TR>
            <TD class="rowodd" width="40%"><xsl:value-of select="fileName" /></TD>
            <TD class="roweven" width="30%"><xsl:value-of select="size" /></TD>
            <TD class="roweven" width="30%"><xsl:value-of select="checksum" /></TD>
        </TR>
    </xsl:template>
</xsl:stylesheet>