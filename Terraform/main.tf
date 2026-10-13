terraform {
  required_version = ">= 1.7.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_instance" "Note-Application" {
  instance_id = var.ec2_instance_id
}

data "aws_network_interface" "application" {
  id = data.aws_instance.Note-Application.network_interface_id
}

resource "aws_security_group" "note-app-sg" {
  # Preserve legacy state addresses and immutable name/description to avoid replacement.
  name        = var.security_group_name
  description = "Security group for Note application"
  vpc_id      = data.aws_network_interface.application.vpc_id

  tags = {
    Name    = "leckeep-app-sg"
    Project = "LecKeep"
  }

  lifecycle {
    prevent_destroy = true
  }

  # SSH access
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.admin_ssh_cidr]
    description = "SSH from the trusted administrator IPv4 range only"
  }

  # Nginx HTTP redirect and ACME certificate validation.
  ingress {
    from_port        = 80
    to_port          = 80
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = length(data.aws_instance.Note-Application.ipv6_addresses) > 0 ? ["::/0"] : []
    description      = "Public HTTP for Nginx redirects and ACME"
  }

  # Nginx HTTPS is the public application entry point.
  ingress {
    from_port        = 443
    to_port          = 443
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = length(data.aws_instance.Note-Application.ipv6_addresses) > 0 ? ["::/0"] : []
    description      = "Public HTTPS for LecKeep"
  }

  # Image/package downloads, DNS, certificate renewal, and external APIs need outbound access.
  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = length(data.aws_instance.Note-Application.ipv6_addresses) > 0 ? ["::/0"] : []
  }
}

resource "aws_network_interface_sg_attachment" "sg_attachment" {
  security_group_id    = aws_security_group.note-app-sg.id
  network_interface_id = data.aws_instance.Note-Application.network_interface_id

  # Add only this group; do not take ownership of the instance's complete SG set.
  lifecycle {
    prevent_destroy = true
  }
}

output "instance_public_dns" {
  description = "Public DNS of the existing application instance."
  value       = data.aws_instance.Note-Application.public_dns
}

output "security_group_id" {
  description = "LecKeep's managed security group."
  value       = aws_security_group.note-app-sg.id
}

output "attached_security_group_ids" {
  description = "Groups observed on the existing instance; audit their combined rules before applying."
  value       = sort(tolist(data.aws_instance.Note-Application.vpc_security_group_ids))
}
