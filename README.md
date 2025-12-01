DCAT-AP Exporter for Dataverse
==============================

This exporter is based on the DCAT-AP specification for describing datasets in a standardized way. 
It allows users to export metadata from Dataverse Datasets into aDCAT-AP compliant format.

> [!WARNING]
> This exporter is a work in progress and may not yet fully comply with the DCAT-AP specification.

Installation
------------
After building the jar with `mvn clean install`, 
you can copy this into a directory on the server where Dataverse can load it. 
Dataverse must be configured to load extra exporters from this directory. 
See the Dataverse documentation for more details:
https://guides.dataverse.org/en/latest/installation/config.html#dataverse-spi-exporters-directory

