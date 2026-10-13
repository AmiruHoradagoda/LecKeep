variable "aws_region" {
  type        = string
  description = "AWS region containing the existing application EC2 instance."
  default     = "us-east-1"
}

variable "ec2_instance_id" {
  type        = string
  description = "Existing application EC2 instance ID; Terraform does not manage its lifecycle."

  validation {
    condition     = can(regex("^i-([0-9a-f]{8}|[0-9a-f]{17})$", var.ec2_instance_id))
    error_message = "Supply an existing EC2 instance ID in the selected AWS region."
  }
}

variable "admin_ssh_cidr" {
  type        = string
  description = "Trusted administrator IPv4 CIDR for SSH. Prefer one public IP as /32; never allow the entire internet."

  validation {
    condition = (
      can(cidrnetmask(var.admin_ssh_cidr)) &&
      can(regex("/([1-9]|[12][0-9]|3[0-2])$", var.admin_ssh_cidr))
    )
    error_message = "Use a valid trusted IPv4 CIDR with prefix /1 through /32, preferably /32. IPv6 and /0 are not accepted for SSH."
  }
}

variable "security_group_name" {
  type        = string
  description = "Keep the existing managed group's AWS name. Changing it forces replacement and is blocked by prevent_destroy."
  default     = "note-app-sg"

  validation {
    condition     = length(trimspace(var.security_group_name)) > 0
    error_message = "The security group name must not be empty."
  }
}
