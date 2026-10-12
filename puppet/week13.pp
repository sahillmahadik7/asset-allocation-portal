file { 'C:/ProgramData/AssetPortal/config':
  ensure => directory,
}

file { 'C:/ProgramData/AssetPortal/config/app-config.txt':
  ensure  => file,
  content => "application=asset-allocation-portal\nmanaged_by=puppet\n",
  require => File['C:/ProgramData/AssetPortal/config'],
}
