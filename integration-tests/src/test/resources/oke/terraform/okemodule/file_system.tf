/*
# Copyright (c) 2024, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
*/


resource "oci_file_storage_file_system" "okemar_fs1" {
  #Required
  availability_domain = var.availability_domain

  #availability_domain = data.oci_identity_availability_domain.ad1.name
  compartment_id      = var.compartment_id
}
resource "oci_file_storage_file_system" "okemar_fs2" {
  #Required
  availability_domain = var.availability_domain
  compartment_id      = var.compartment_id
}
