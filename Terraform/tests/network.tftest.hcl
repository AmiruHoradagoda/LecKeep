# Plan-only mocked tests: no AWS credentials, API calls, or resource changes.
mock_provider "aws" {}

override_data {
  target = data.aws_instance.Note-Application
  values = {
    id                     = "i-0123456789abcdef0"
    network_interface_id   = "eni-0123456789abcdef0"
    public_dns             = "ec2.example.test"
    ipv6_addresses         = []
    vpc_security_group_ids = ["sg-0123456789abcdef0", "sg-0fedcba9876543210"]
  }
}

override_data {
  target = data.aws_network_interface.application
  values = {
    id              = "eni-0123456789abcdef0"
    vpc_id          = "vpc-0123456789abcdef0"
    security_groups = ["sg-0123456789abcdef0", "sg-0fedcba9876543210"]
  }
}

variables {
  ec2_instance_id = "i-0123456789abcdef0"
  admin_ssh_cidr  = "203.0.113.10/32"
}

run "ipv4_public_web_and_restricted_ssh" {
  command = plan

  assert {
    condition = (
      length(aws_security_group.note-app-sg.ingress) == 3 &&
      alltrue([for rule in aws_security_group.note-app-sg.ingress :
        rule.protocol == "tcp" && rule.from_port == rule.to_port &&
        contains([22, 80, 443], rule.from_port) &&
        toset(rule.cidr_blocks) == toset(rule.from_port == 22 ? [var.admin_ssh_cidr] : ["0.0.0.0/0"]) &&
        length(coalesce(rule.ipv6_cidr_blocks, [])) == 0
      ])
    )
    error_message = "Only TCP 80/443 may be public; TCP 22 must use the trusted CIDR, without IPv6 or application-port exposure."
  }

  assert {
    condition     = aws_security_group.note-app-sg.vpc_id == "vpc-0123456789abcdef0"
    error_message = "The security group must use the existing instance's VPC, not an assumed default VPC."
  }

  assert {
    condition     = aws_network_interface_sg_attachment.sg_attachment.network_interface_id == "eni-0123456789abcdef0"
    error_message = "Attach only to the existing instance's primary network interface."
  }

  assert {
    condition     = toset(output.attached_security_group_ids) == toset(["sg-0123456789abcdef0", "sg-0fedcba9876543210"])
    error_message = "Existing security groups must remain visible for review rather than assuming a single SG."
  }
}

run "existing_ipv6_allows_web_only" {
  command = plan

  override_data {
    target = data.aws_instance.Note-Application
    values = {
      network_interface_id   = "eni-0123456789abcdef0"
      public_dns             = "ec2.example.test"
      ipv6_addresses         = ["2001:db8::10"]
      vpc_security_group_ids = ["sg-0123456789abcdef0"]
    }
  }

  assert {
    condition = alltrue([for rule in aws_security_group.note-app-sg.ingress :
      toset(coalesce(rule.ipv6_cidr_blocks, [])) == toset(rule.from_port == 22 ? [] : ["::/0"])
    ])
    error_message = "Existing IPv6 may expose only HTTP/HTTPS, never SSH or application ports."
  }
}

run "reject_public_ssh" {
  command = plan
  variables {
    admin_ssh_cidr = "0.0.0.0/0"
  }
  expect_failures = [var.admin_ssh_cidr]
}

run "reject_invalid_ssh_cidr" {
  command = plan
  variables {
    admin_ssh_cidr = "not-a-cidr"
  }
  expect_failures = [var.admin_ssh_cidr]
}

run "reject_ipv6_ssh_cidr" {
  command = plan
  variables {
    admin_ssh_cidr = "::/0"
  }
  expect_failures = [var.admin_ssh_cidr]
}
