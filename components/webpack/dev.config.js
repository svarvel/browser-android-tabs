const path = require('path')
const webpack = require('webpack')

module.exports = {
  mode: 'development',
  devtool: '#inline-source-map',
  entry: {
    brave_rewards: path.join(__dirname, '../brave_rewards_ui/resources/brave_rewards'),
    brave_rewards_donate: path.join(__dirname, '../brave_rewards_ui/donate/brave_rewards/brave_rewards_donate'),
    brave_rewards_panel: path.join(__dirname, '../brave_rewards_ui/panel/brave_rewards/brave_rewards_panel'),
    brave_rewards_panel_background: path.join(__dirname, '../brave_rewards_ui/panel/brave_rewards/background')
  },
  output: {
    path: process.env.TARGET_GEN_DIR,
    filename: '[name].js',
    chunkFilename: '[id].chunk.js'
  },
  plugins: [
    new webpack.optimize.OccurrenceOrderPlugin(),
    new webpack.IgnorePlugin(/[^/]+\/[\S]+.dev$/)    
  ],
  resolve: {
    extensions: ['.js', '.tsx', '.ts', '.json'],
  },
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        loader: 'awesome-typescript-loader'
      },
      {
        test: /\.js$/,
        loader: 'babel-loader',
        exclude: /node_modules/,
        query: {
          presets: ['react-optimize']
        }
      },
      {
        test: /\.less$/,
        loader: 'style-loader!css-loader?-minimize!less-loader'
      },
      {
        test: /\.css$/,
        loader: 'style-loader!css-loader?-minimize'
      },
      // Loads font files for Font Awesome
      {
        test: /\.woff(2)?(\?v=[0-9]\.[0-9]\.[0-9])?$/,
        loader: 'url-loader?limit=13000&minetype=application/font-woff'
      },
      {
        test: /\.(ttf|eot|svg|png|jpg)(\?v=[0-9]\.[0-9]\.[0-9])?$/,
        loader: 'file-loader'
      }]
  },
  node: {
    fs: 'empty'
  }
}
