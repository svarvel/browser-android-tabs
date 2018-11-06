const path = require('path')
const webpack = require('webpack')
const productionConfig = require('./prod.config.js')

module.exports = Object.assign({}, productionConfig, {
  mode: 'development',
  devtool: '#inline-source-map'
})
    brave_rewards_donate: path.join(__dirname, '../brave_rewards_ui/donate/brave_rewards/brave_rewards_donate'),
